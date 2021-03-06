/*
 * Copyright (c) 2013-2014 Massachusetts Institute of Technology
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package edu.mit.commensalejml.impl;

import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.SetMultimap;
import edu.mit.streamjit.util.bytecode.Argument;
import edu.mit.streamjit.util.bytecode.Field;
import edu.mit.streamjit.util.bytecode.Method;
import edu.mit.streamjit.util.bytecode.Value;
import edu.mit.streamjit.util.bytecode.methodhandles.Combinators;
import edu.mit.streamjit.util.bytecode.methodhandles.LookupUtils;
import static edu.mit.streamjit.util.bytecode.methodhandles.LookupUtils.findVirtual;
import static edu.mit.streamjit.util.bytecode.methodhandles.LookupUtils.params;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static java.util.stream.Collectors.toList;
import org.ejml.data.D1Matrix64F;
import org.ejml.data.DenseMatrix64F;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 10/3/2014
 */
public final class GreedyCodegen {
	private final Object stateHolder;
	private final Map<Value, Field> fieldMap;
	private final Method method;
	private final ExpressionDAG result;
	private final Map<Expr, MethodHandle> ready = new LinkedHashMap<>();
	private final Set<Expr> worklist = new LinkedHashSet<>();
	//the number of "leases" on temporary matrices
	private final Multiset<MethodHandle> allocatedTemps = HashMultiset.create();
	private final Map<MatrixDimension, Deque<MethodHandle>> tempFreelist;
	private final SetMultimap<Expr, Expr> remainingUses = HashMultimap.create();
	private final Map<Input, MethodHandle> deferredFieldSets = new LinkedHashMap<>();
	private MethodHandle deferredRet = null;

	public GreedyCodegen(Object stateHolder, Map<Value, Field> fieldMap, Method method, ExpressionDAG result, Map<MatrixDimension, Deque<MethodHandle>> tempFreelist) {
		this.stateHolder = stateHolder;
		this.fieldMap = fieldMap;
		this.method = method;
		this.result = result;
		this.tempFreelist = tempFreelist;
	}

	public MethodHandle codegen() {
		result.roots().forEachOrdered(this::prepare);
		List<MethodHandle> ops = new ArrayList<>();
		while (!worklist.isEmpty()) {
			Expr next = popNext();

			List<MethodHandle> sources = next.deps().stream().map(this::source).collect(toList());

			//inplace sources are now free
			next.inplacePlaces().stream().forEachOrdered((d) -> remainingUses.get(d).remove(next));
			next.inplacePlaces().stream().distinct().forEachOrdered(this::tryFree);

			MethodHandle sink = sink(next);
			ops.add(next.operate(sources, sink));
			ready.put(next, sink);

			//If this expression sets a field and we didn't allocate it as a
			//sink, we have to set it later.  (We'll process deferred sets
			//after each expr, so we might do it immediately.)
			Field destField = result.sets.inverse().get(next);
			if (destField != null && sink != makeFieldGetter(destField)) {
				Input destInput = result.inputs.get(destField);
				deferredFieldSets.put(destInput, sink);
				//take another temp lease
				if (allocatedTemps.contains(sink))
					allocatedTemps.add(sink);
				//Field sets aren't considered uses, so we can free it now.
				tryFree(next);
			}
			//If this is the return value, it's live until method end.
			if (result.ret == next) {
				deferredRet = sink;
				//take another temp lease
				if (allocatedTemps.contains(sink))
					allocatedTemps.add(sink);
				//Returns aren't considered uses, so we can free it now.
				tryFree(next);
			}

			//non-inplace sources are now free
			next.deps().stream().filter((d) -> !next.inplacePlaces().contains(d)).forEachOrdered((d) -> remainingUses.get(d).remove(next));
			next.deps().stream().filter((d) -> !next.inplacePlaces().contains(d)).distinct().forEachOrdered(this::tryFree);

			ops.addAll(undeferSets());
		}
		assert deferredFieldSets.isEmpty() : deferredFieldSets;

		MethodHandle withArgs = Combinators.semicolon(ops);
		for (Argument a : FluentIterable.from(method.arguments()).skip(1))
			withArgs = MethodHandles.collectArguments(withArgs, withArgs.type().parameterCount(), makeFieldSetter(fieldMap.get(a)));

		if (deferredRet != null) {
			withArgs = MethodHandles.filterReturnValue(withArgs, deferredRet);
			tryExpireTemp(deferredRet);
		}
		assert allocatedTemps.isEmpty() : allocatedTemps;
		return withArgs;
	}

	private void prepare(Expr e) {
		if (e instanceof Input)
			ready.put(e, makeFieldGetter(((Input)e).getField()));
		else
			worklist.add(e);
		e.deps().forEach(d -> remainingUses.put(d, e));
		e.deps().forEach(this::prepare);
	}

	private Expr popNext() {
		for (Expr e : worklist)
			if (ready.keySet().containsAll(e.deps())) {
				worklist.remove(e);
				return e;
			}
		throw new AssertionError("nothing with all deps ready");
	}

	private MethodHandle source(Expr e) {
		return ready.computeIfAbsent(e, (Expr x) -> {
			throw new AssertionError(e + " is not ready");
		});
	}

	/**
	 * Finds a sink for the result of the given expression.  If inplace is
	 * possible, inplace will be done; else a temporary will be allocated.
	 * Despite intuition, sinks are getter handles, not setters.
	 */
	private MethodHandle sink(Expr e) {
		//Field outputs should go direct if we don't still need the
		//corresponding input.
		//TODO: I guess we'd want some lookahead to see if going direct is
		//possible early, to set up an inplace operation targeting the output.
		Field output = result.sets.inverse().get(e);
		Input input = result.inputs.get(output);
		if (output != null && remainingUses.get(input).isEmpty())
			return makeFieldGetter(input.getField());
		assert e.rows() != -1 && e.cols() != -1;
		Deque<MethodHandle> freelist = tempFreelist.computeIfAbsent(new MatrixDimension(e.rows(), e.cols()), (MatrixDimension d) -> new ArrayDeque<>());
		if (freelist.isEmpty())
			freelist.push(MethodHandles.constant(DenseMatrix64F.class, new DenseMatrix64F(e.rows(), e.cols())));
		MethodHandle temp = freelist.pop();
		assert !allocatedTemps.contains(temp) : "free list contained temp already allocated";
		allocatedTemps.add(temp);
		return temp;
	}

	private void tryFree(Expr e) {
		MethodHandle loc = ready.get(e);
		//We took another lease on deferred field sets or ret so we don't
		//need to check that here; we can still unready it.
		if (remainingUses.get(e).isEmpty()) {
			ready.remove(e);
			tryExpireTemp(loc);
		}
	}

	private void tryExpireTemp(MethodHandle loc) {
		//if loc is a temp and we expired its last lease
		if (allocatedTemps.remove(loc) && !allocatedTemps.contains(loc))
			try {
				tempFreelist.get(new MatrixDimension((DenseMatrix64F)loc.invokeExact())).push(loc);
			} catch (Throwable ex) {
				throw Throwables.propagate(ex);
			}
	}

	private List<MethodHandle> undeferSets() {
		List<MethodHandle> setOps = new ArrayList<>();
		for (Iterator<Map.Entry<Input, MethodHandle>> i = deferredFieldSets.entrySet().iterator(); i.hasNext();) {
			Map.Entry<Input, MethodHandle> entry = i.next();
			Input input = entry.getKey();
			MethodHandle temp = entry.getValue();
			if (remainingUses.get(input).isEmpty()) {
				setOps.add(setField(input.getField(), temp));
				tryExpireTemp(temp);
				i.remove();
			}
		}
		return setOps;
	}
	private final MethodHandle SET_ = findVirtual(D1Matrix64F.class, "set", params(1));
	private final MethodHandle SET = SET_.asType(MethodType.methodType(void.class, DenseMatrix64F.class, DenseMatrix64F.class));

	private MethodHandle setField(Field field, MethodHandle source) {
		MethodHandle sink = makeFieldGetter(field);
		MethodHandle set = MethodHandles.collectArguments(SET, 0, sink);
		return MethodHandles.collectArguments(set, 0, source);
	}

	private final Map<Field, MethodHandle> fieldGetterCache = new IdentityHashMap<>();
	private MethodHandle makeFieldGetter(Field f) {
		//TODO: if we know the Input's matrix (i.e., not an arg), should we
		//make a constant handle instead?
		return fieldGetterCache.computeIfAbsent(f, (Field f_) ->
				LookupUtils.findGetter(stateHolder.getClass(), f_.getName(), DenseMatrix64F.class).bindTo(stateHolder));
	}

	private MethodHandle makeFieldSetter(Field f) {
		return LookupUtils.findSetter(stateHolder.getClass(), f.getName(), DenseMatrix64F.class).bindTo(stateHolder);
	}

}
