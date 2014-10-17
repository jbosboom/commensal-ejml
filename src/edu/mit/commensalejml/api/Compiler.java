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
package edu.mit.commensalejml.api;

import edu.mit.commensalejml.impl.GreedyCodegen;
import edu.mit.commensalejml.impl.ExpressionDAG;
import edu.mit.commensalejml.test.kalman.KalmanFilter;
import edu.mit.commensalejml.test.kalman.KalmanFilterSimple;
import static com.google.common.base.Preconditions.checkArgument;
import com.google.common.collect.BiMap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import static com.google.common.collect.Iterables.getOnlyElement;
import edu.mit.commensalejml.impl.Expr;
import edu.mit.commensalejml.impl.Input;
import edu.mit.commensalejml.impl.Invert;
import edu.mit.commensalejml.impl.MatrixDimension;
import edu.mit.commensalejml.impl.Minus;
import edu.mit.commensalejml.impl.Multiply;
import edu.mit.commensalejml.impl.Plus;
import edu.mit.commensalejml.impl.Transpose;
import edu.mit.streamjit.util.bytecode.Access;
import edu.mit.streamjit.util.bytecode.Argument;
import edu.mit.streamjit.util.bytecode.BasicBlock;
import edu.mit.streamjit.util.bytecode.Field;
import edu.mit.streamjit.util.bytecode.Klass;
import edu.mit.streamjit.util.bytecode.Method;
import edu.mit.streamjit.util.bytecode.Modifier;
import edu.mit.streamjit.util.bytecode.Module;
import edu.mit.streamjit.util.bytecode.ModuleClassLoader;
import edu.mit.streamjit.util.bytecode.Value;
import edu.mit.streamjit.util.bytecode.insts.CallInst;
import edu.mit.streamjit.util.bytecode.insts.CastInst;
import edu.mit.streamjit.util.bytecode.insts.Instruction;
import edu.mit.streamjit.util.bytecode.insts.LoadInst;
import edu.mit.streamjit.util.bytecode.insts.ReturnInst;
import edu.mit.streamjit.util.bytecode.insts.StoreInst;
import edu.mit.streamjit.util.bytecode.methodhandles.ProxyFactory;
import edu.mit.streamjit.util.bytecode.types.TypeFactory;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.ejml.data.DenseMatrix64F;
import org.ejml.simple.SimpleMatrix;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 7/6/2014
 */
public final class Compiler {
	private final Module module = new Module();
	private final ModuleClassLoader loader = new ModuleClassLoader(module);
	private final Klass simpleMatrix = module.getKlass(SimpleMatrix.class);
	private final Klass denseMatrix = module.getKlass(DenseMatrix64F.class);
	private Object stateHolder;
	/**
	 * Maps fields and arguments in the original class to fields in the state
	 * holder class.
	 */
	private final Map<Value, Field> fieldMap = new HashMap<>();
	private final Map<Field, Object> knownFieldValues = new HashMap<>();
	public Compiler() {}

	public <T> T compile(Class<T> iface, Class<? extends T> c, Object... ctorArgs) {
		checkArgument(iface.isInterface(), "%s not an interface", iface);
		checkArgument(iface.isAssignableFrom(c), "%s does not implement %s", iface, c);
		Klass k = module.getKlass(c);
		k.methods().forEach(Method::resolve);

		makeStateHolder(k, ctorArgs);
		Map<String, MethodHandle> impls = makeImplHandles(k);
		return new ProxyFactory(loader).createProxy("asdfasdf", impls, iface);
	}

	private void makeStateHolder(Klass k, Object[] ctorArgs) {
		TypeFactory types = module.types();
		Klass stateHolderKlass = new Klass("StateHolder", module.getKlass(Object.class), ImmutableList.<Klass>of(),
				EnumSet.of(Modifier.PUBLIC, Modifier.FINAL), module);
		for (Field f : k.fields()) {
			if (f.isStatic()) continue;
			if (f.getType().getFieldType().getKlass() != simpleMatrix)
				throw new UnsupportedOperationException(f.toString());
			Field n = new Field(types.getRegularType(denseMatrix),
					f.getName(), f.modifiers(), stateHolderKlass);
			n.modifiers().add(Modifier.FINAL);
			n.setAccess(Access.PUBLIC);
			fieldMap.put(f, n);
		}

		for (Method m : k.methods()) {
			if (m.isConstructor()) continue;
			for (Argument a : m.arguments()) {
				if (a.isReceiver()) continue;
				if (a.getType().getKlass() != denseMatrix)
					throw new UnsupportedOperationException(a.getType()+" "+a.toString());
				Field n = new Field(types.getRegularType(denseMatrix),
						m.getName()+"$"+a.getName(), EnumSet.of(Modifier.PUBLIC), stateHolderKlass);
				fieldMap.put(a, n);
			}
		}

		Method oldInit = k.getMethods("<init>").iterator().next();
		Method newInit = new Method("<init>", oldInit.getType().withReturnType(types.getType(stateHolderKlass)), EnumSet.of(Modifier.PUBLIC), stateHolderKlass);
		BasicBlock initBlock = new BasicBlock(module);
		newInit.basicBlocks().add(initBlock);
		Method superCtor = getOnlyElement(module.getKlass(Object.class).getMethods("<init>"));
		initBlock.instructions().add(new CallInst(superCtor));

		//TODO: makes assumptions
		Method denseMatrixCopy = denseMatrix.getMethod("copy",
				types.getMethodType(DenseMatrix64F.class, DenseMatrix64F.class));
		Argument newThis = newInit.arguments().get(0);
		for (StoreInst oldStore : FluentIterable.from(oldInit.basicBlocks().get(0).instructions()).filter(StoreInst.class)) {
			Argument source = (Argument)((CallInst)oldStore.getData()).getArgument(0);
			int argNo = source.getParent().arguments().indexOf(source);
			Field field = fieldMap.get(oldStore.getLocation());
			if (field.modifiers().contains(Modifier.FINAL))
				knownFieldValues.put(field, ctorArgs[argNo-1]);

			CallInst copy = new CallInst(denseMatrixCopy, newInit.arguments().get(argNo));
			StoreInst newStore = new StoreInst(field, copy, newThis);
			initBlock.instructions().addAll(ImmutableList.of(copy, newStore));
		}

		initBlock.instructions().add(new ReturnInst(types.getVoidType()));

		try {
			Class<?> stateHolderClass = loader.loadClass("StateHolder");
			Constructor<?>[] ctors = stateHolderClass.getConstructors();
			assert ctors.length == 1;
			stateHolder = ctors[0].newInstance(ctorArgs);
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
			throw new AssertionError(ex);
		}
	}

	private Map<String, MethodHandle> makeImplHandles(Klass k) {
		Map<MatrixDimension, Deque<MethodHandle>> tempFreelist = new LinkedHashMap<>();
		Map<String, MethodHandle> impls = new HashMap<>();
		for (Method m : k.methods()) {
			if (m.isConstructor()) continue;
			ExpressionDAG result = buildIR(m);
			System.out.println(m.getName());
			result.roots().forEachOrdered(Compiler::foldMultiplyTranspose);
			impls.put(m.getName(), new GreedyCodegen(stateHolder, fieldMap, m, result, tempFreelist).codegen());
		}
		return impls;
	}

	private ExpressionDAG buildIR(Method m) {
		Map<Value, Expr> exprs = new IdentityHashMap<>();
		Map<Field, Input> fieldInputs = new IdentityHashMap<>();
		//strictly speaking, multiple blocks okay if all terminators are jump/ret
		if (m.basicBlocks().size() > 1)
			throw new UnsupportedOperationException(m.getName());
		for (Argument a : m.arguments()) {
			if (a.isReceiver()) continue;
			Field f = fieldMap.get(a);
			Input input = fieldInputs.computeIfAbsent(f,  f_ ->
					new Input(f_, (DenseMatrix64F)knownFieldValues.get(f_)));
			exprs.put(f, input);
		}

		BiMap<Field, Expr> sets = HashBiMap.create();
		Expr ret = null;
		for (Instruction i : getOnlyElement(m.basicBlocks()).instructions()) {
			if (i instanceof LoadInst) {
				Field f = fieldMap.get(((LoadInst)i).getLocation());
				//We might load this field multiple times, but it's the same input.
				Input input = fieldInputs.computeIfAbsent(f,  f_ ->
						new Input(f_, (DenseMatrix64F)knownFieldValues.get(f_)));
				exprs.put(i, input);
			} else if (i instanceof CastInst)
				exprs.put(i, exprs.get(i.getOperand(0)));
			else if (i instanceof CallInst) {
				CallInst ci = (CallInst)i;
				Method op = ci.getMethod();
				String name = op.getName();
				if (name.equals("getMatrix") || name.equals("wrap") || name.equals("<init>"))
					exprs.put(i, exprs.get(fieldMap.get(ci.getArgument(0))));
				else if (name.equals("invert"))
					exprs.put(i, new Invert(exprs.get(ci.getArgument(0))));
				else if (name.equals("transpose"))
					exprs.put(i, new Transpose(exprs.get(ci.getArgument(0))));
				else if (name.equals("plus"))
					exprs.put(i, new Plus(
							exprs.get(ci.getArgument(0)),
							exprs.get(ci.getArgument(1))));
				else if (name.equals("minus"))
					exprs.put(i, new Minus(
							exprs.get(ci.getArgument(0)),
							exprs.get(ci.getArgument(1))));
				else if (name.equals("mult"))
					exprs.put(i, Multiply.regular(
							exprs.get(ci.getArgument(0)),
							exprs.get(ci.getArgument(1))));
				else
					throw new UnsupportedOperationException(op.toString());
			} else if (i instanceof StoreInst) {
				Field f = fieldMap.get(((StoreInst)i).getLocation());
				sets.put(f, exprs.get(((StoreInst)i).getData()));
			} else if (i instanceof ReturnInst) {
				if (i.getNumOperands() == 0) continue;
				ret = exprs.get(i.getOperand(0));
			} else
				throw new UnsupportedOperationException(i.toString());
		}
		return new ExpressionDAG(fieldInputs, sets, ret);
	}

	private static void foldMultiplyTranspose(Expr e) {
		if (e instanceof Multiply) {
			Multiply m = (Multiply)e;
			Expr left = m.deps().get(0), right = m.deps().get(1);
			if (left instanceof Transpose) {
				System.out.println("folded away left "+left);
				m.deps().set(0, left.deps().get(0));
				m.toggleTransposeLeft();
			}
			if (right instanceof Transpose) {
				System.out.println("folded away right "+right);
				m.deps().set(1, right.deps().get(0));
				m.toggleTransposeRight();
			}
		}
		e.deps().forEach(Compiler::foldMultiplyTranspose);
	}

	private static void print(Expr e, int indentLevel) {
		for (int i = 0; i < indentLevel; ++i)
			System.out.print("  ");
		System.out.println(e);
		for (Expr d : e.deps())
			print(d, indentLevel+1);
	}

	public static void main(String[] args) {
		System.out.println(new Compiler().compile(KalmanFilter.class, KalmanFilterSimple.class,
				new DenseMatrix64F(9, 9), new DenseMatrix64F(9, 9), new DenseMatrix64F(8, 9),
				new DenseMatrix64F(9, 1), new DenseMatrix64F(9, 9)));
	}
}
