package edu.mit.commensalejml.api;

import com.google.common.collect.BiMap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import static com.google.common.collect.Iterables.getOnlyElement;
import com.google.common.collect.Ordering;
import com.google.common.collect.SetMultimap;
import edu.mit.commensalejml.impl.Expr;
import edu.mit.commensalejml.impl.Input;
import edu.mit.commensalejml.impl.Invert;
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
import edu.mit.streamjit.util.bytecode.types.TypeFactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.ejml.data.DenseMatrix64F;
import org.ejml.simple.SimpleMatrix;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 7/6/2014
 */
public final class Compiler {
	private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
	private final Module module = new Module();
	private final ModuleClassLoader loader = new ModuleClassLoader(module);
	private final Klass simpleMatrix = module.getKlass(SimpleMatrix.class);
	private final Klass denseMatrix = module.getKlass(DenseMatrix64F.class);
	private Klass stateHolderKlass;
	private Object stateHolder;
	/**
	 * Maps fields and arguments in the original class to fields in the state
	 * holder class.
	 */
	private final Map<Value, Field> fieldMap = new HashMap<>();
	private final Map<Field, Object> knownFieldValues = new HashMap<>();
	/**
	 * Maps methods in the original class to the corresponding IR.
	 */
	private final Map<Method, Result> methodIR = new HashMap<>();
	public Compiler() {}

	public <T> T compile(Class<? extends T> c, Object... ctorArgs) {
		Klass k = module.getKlass(c);
		for (Method m : k.methods())
			m.resolve();
//		k.dump(System.out);

		makeStateHolder(k, ctorArgs);
//		stateHolder.dump(System.out);

		for (Method m : k.methods()) {
			if (m.isConstructor()) continue;
			Result result = buildIR(m);
			System.out.println(m.getName());
			for (Expr e : result.sets.values()) {
				foldMultiplyTranspose(e);
				System.out.println(e.rows()+" "+e.cols());
//				print(e, 0);
			}
			if (result.ret != null) {
				foldMultiplyTranspose(result.ret);
				System.out.println(result.ret.rows()+" "+result.ret.cols());
//				print(result.ret, 0);
			}
			new GreedyCodegen(result).codegen();
		}
		return null;
	}

	private void makeStateHolder(Klass k, Object[] ctorArgs) {
		TypeFactory types = module.types();
		stateHolderKlass = new Klass("StateHolder", module.getKlass(Object.class), ImmutableList.<Klass>of(), module);
		stateHolderKlass.setAccess(Access.PUBLIC);
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
				//TODO: should be "isReceiver"
				if (a.getName().equals("this")) continue;
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

	private Result buildIR(Method m) {
		Map<Value, Expr> exprs = new IdentityHashMap<>();
		Map<Field, Input> fieldInputs = new IdentityHashMap<>();
		//strictly speaking, multiple blocks okay if all terminators are jump/ret
		if (m.basicBlocks().size() > 1)
			throw new UnsupportedOperationException(m.getName());
		for (Argument a : m.arguments()) {
			//TODO: isReceiver
			if (a.getName().equals("this")) continue;
			Field f = fieldMap.get(a);
			exprs.put(a, new Input(f, (DenseMatrix64F)knownFieldValues.get(f)));
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
					exprs.put(i, exprs.get(ci.getArgument(0)));
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
		return new Result(fieldInputs, sets, ret);
	}

	private static final class Result {
		public final Map<Field, Input> inputs;
		public final BiMap<Field, Expr> sets;
		public final Expr ret;
		Result(Map<Field, Input> inputs, BiMap<Field, Expr> sets, Expr ret) {
			this.inputs = inputs;
			this.sets = sets;
			this.ret = ret;
		}
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

		for (Expr d : e.deps())
			foldMultiplyTranspose(d);
	}

	private static void print(Expr e, int indentLevel) {
		for (int i = 0; i < indentLevel; ++i)
			System.out.print("  ");
		System.out.println(e);
		for (Expr d : e.deps())
			print(d, indentLevel+1);
	}

	private final class GreedyCodegen {
		private final Result result;
		//ordered by hotness (hottest first)
		private final List<Expr> ready = new ArrayList<>();
		private final Set<Expr> unready = new HashSet<>();
		private final Map<Expr, DenseMatrix64F> allocatedTemps = new HashMap<>();
		private final List<DenseMatrix64F> tempFreelist = new ArrayList<>();
		private final SetMultimap<Expr, Expr> remainingUses = HashMultimap.create();
		GreedyCodegen(Result result) {
			this.result = result;
		}
		public MethodHandle codegen() {
			for (Expr e : result.sets.values())
				prepare(e);
			if (result.ret != null)
				prepare(result.ret);

			List<MethodHandle> ops = new ArrayList<>();
			while (!unready.isEmpty()) {
				Expr next = findNext();
				unready.remove(next);

				List<MethodHandle> sources = new ArrayList<>();
				for (Expr d : next.deps())
					sources.add(source(d));
				MethodHandle sink = sink(next);
				ops.add(next.operate(sources, sink));

				ready.add(0, next);
				//TODO: expire temps to free list?  what about deferred field sets or ret?
			}

			return null;
		}

		private void prepare(Expr e) {
			if (e instanceof Input) {
				if (!ready.contains(e))
					ready.add(e);
			} else
				unready.add(e);
			for (Expr d : e.deps())
				remainingUses.put(d, e);
			for (Expr d : e.deps())
				prepare(d);
		}

		private Expr findNext() {
			List<Expr> possibleNext = new ArrayList<>();
			for (Expr e : unready)
				if (ready.containsAll(e.deps()))
					possibleNext.add(e);
			Collections.sort(possibleNext, new Comparator<Expr>() {
				@Override
				public int compare(Expr left, Expr right) {
					List<Integer> leftDepsRank = new ArrayList<>(),
							rightDepsRank = new ArrayList<>();
					for (Expr e : left.deps())
						leftDepsRank.add(ready.indexOf(e));
					for (Expr e : right.deps())
						rightDepsRank.add(ready.indexOf(e));
					Collections.sort(leftDepsRank);
					Collections.sort(rightDepsRank);
					return Ordering.<Integer>natural().lexicographical().compare(leftDepsRank, rightDepsRank);
				}
			});
			return possibleNext.get(0);
		}

		private MethodHandle source(Expr e) {
			//TODO: cache various handles?
			if (e instanceof Input)
				try {
					return LOOKUP.findGetter(stateHolder.getClass(), ((Input)e).getField().getName(), DenseMatrix64F.class).bindTo(stateHolder);
				} catch (NoSuchFieldException | IllegalAccessException ex) {
					throw new AssertionError(ex);
				}
			else {
				assert allocatedTemps.containsKey(e);
				return MethodHandles.constant(DenseMatrix64F.class, allocatedTemps.get(e));
			}
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
			if (remainingUses.get(input).isEmpty())
				return source(input);

			for (Expr i : e.inplacePlaces())
				if (!(i instanceof Input) && remainingUses.get(i).isEmpty())
					return source(i);

			assert e.rows() != -1 && e.cols() != -1;
			DenseMatrix64F temp = tempFreelist.stream()
					.filter(m -> m.getNumRows() == e.rows() && m.getNumCols() == e.cols())
					.findFirst()
					.orElseGet(() -> new DenseMatrix64F(e.rows(), e.cols()));
			tempFreelist.remove(temp);
			allocatedTemps.put(e, temp);
			return MethodHandles.constant(DenseMatrix64F.class, temp);
		}
	}

	public static void main(String[] args) {
		System.out.println(new Compiler().compile(KalmanFilterSimple.class,
				new DenseMatrix64F(9, 9), new DenseMatrix64F(9, 9), new DenseMatrix64F(8, 9),
				new DenseMatrix64F(9, 1), new DenseMatrix64F(9, 9)));
	}
}
