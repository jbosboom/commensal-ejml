package edu.mit.commensalejml.api;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import static com.google.common.collect.Iterables.getOnlyElement;
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
import edu.mit.streamjit.util.bytecode.Value;
import edu.mit.streamjit.util.bytecode.insts.CallInst;
import edu.mit.streamjit.util.bytecode.insts.CastInst;
import edu.mit.streamjit.util.bytecode.insts.Instruction;
import edu.mit.streamjit.util.bytecode.insts.LoadInst;
import edu.mit.streamjit.util.bytecode.insts.ReturnInst;
import edu.mit.streamjit.util.bytecode.insts.StoreInst;
import edu.mit.streamjit.util.bytecode.types.TypeFactory;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
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
	private final Klass simpleMatrix = module.getKlass(SimpleMatrix.class);
	private final Klass denseMatrix = module.getKlass(DenseMatrix64F.class);
	private Klass stateHolder;
	/**
	 * Maps fields and arguments in the original class to fields in the state
	 * holder class.
	 */
	private final Map<Value, Field> fieldMap = new HashMap<>();
	public Compiler() {}

	public <T> T compile(Class<? extends T> c, Object... ctorArgs) {
		Klass k = module.getKlass(c);
		for (Method m : k.methods())
			m.resolve();
//		k.dump(System.out);

		makeStateHolder(k);
//		stateHolder.dump(System.out);

		for (Method m : k.methods()) {
			if (m.isConstructor()) continue;
			buildIR(m);
		}
		return null;
	}

	private void makeStateHolder(Klass k) {
		TypeFactory types = module.types();
		stateHolder = new Klass("StateHolder", module.getKlass(Object.class), ImmutableList.<Klass>of(), module);
		stateHolder.setAccess(Access.PUBLIC);
		for (Field f : k.fields()) {
			if (f.isStatic()) continue;
			if (f.getType().getFieldType().getKlass() != simpleMatrix)
				throw new UnsupportedOperationException(f.toString());
			Field n = new Field(types.getRegularType(denseMatrix),
					f.getName(), f.modifiers(), stateHolder);
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
						m.getName()+"$"+a.getName(), EnumSet.of(Modifier.PUBLIC), stateHolder);
				fieldMap.put(a, n);
			}
		}

		Method oldInit = k.getMethods("<init>").iterator().next();
		Method newInit = new Method("<init>", oldInit.getType().withReturnType(types.getType(stateHolder)), EnumSet.of(Modifier.PUBLIC), stateHolder);
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

			CallInst copy = new CallInst(denseMatrixCopy, newInit.arguments().get(argNo));
			StoreInst newStore = new StoreInst(fieldMap.get(oldStore.getLocation()), copy, newThis);
			initBlock.instructions().addAll(ImmutableList.of(copy, newStore));
		}

		initBlock.instructions().add(new ReturnInst(types.getVoidType()));
	}

	private void buildIR(Method m) {
		Map<Value, Expr> exprs = new IdentityHashMap<>();
		//strictly speaking, multiple blocks okay if all terminators are jump/ret
		if (m.basicBlocks().size() > 1)
			throw new UnsupportedOperationException(m.getName());
		for (Argument a : m.arguments()) {
			//TODO: isReceiver
			if (a.getName().equals("this")) continue;
			exprs.put(a, new Input(fieldMap.get(a)));
		}
		for (Instruction i : getOnlyElement(m.basicBlocks()).instructions()) {
			if (i instanceof LoadInst)
				exprs.put(i, new Input(fieldMap.get(((LoadInst)i).getLocation())));
			else if (i instanceof CastInst)
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
					exprs.put(i, new Multiply(
							exprs.get(ci.getArgument(0)),
							exprs.get(ci.getArgument(1))));
				else
					throw new UnsupportedOperationException(op.toString());
			} else if (i instanceof StoreInst) {
				Field f = fieldMap.get(((StoreInst)i).getLocation());
				System.out.println("output "+f.getName()+" = "+exprs.get(((StoreInst)i).getData()));
			} else if (i instanceof ReturnInst) {
				if (i.getNumOperands() == 0) continue;
				System.out.println("output ret = "+exprs.get(i.getOperand(0)));
			} else
				throw new UnsupportedOperationException(i.toString());
		}
	}

	public static void main(String[] args) {
		System.out.println(new Compiler().compile(KalmanFilterSimple.class));
	}
}
