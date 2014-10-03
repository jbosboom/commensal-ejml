package edu.mit.commensalejml.impl;

import com.google.common.collect.BiMap;
import edu.mit.commensalejml.impl.Expr;
import edu.mit.commensalejml.impl.Input;
import edu.mit.streamjit.util.bytecode.Field;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.stream.Stream;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 10/3/2014
 */
public final class ExpressionDAG {
	public final Map<Field, Input> inputs;
	public final BiMap<Field, Expr> sets;
	public final Expr ret;
	public ExpressionDAG(Map<Field, Input> inputs, BiMap<Field, Expr> sets, Expr ret) {
		this.inputs = inputs;
		this.sets = sets;
		this.ret = ret;
	}
	public Stream<Expr> roots() {
		return Stream.concat(sets.values().stream(), ret != null ? Stream.of(ret) : Stream.empty()).distinct();
	}
	public void dump(String filename) {
		try (BufferedWriter w = new BufferedWriter(new FileWriter(filename))) {
			w.write("strict digraph {");
			Deque<Expr> worklist = new ArrayDeque<>();
			for (Field f : sets.keySet()) {
				w.write(q(f.getName())+" [shape=box];\n");
				w.write(q(sets.get(f).toString()) + " -> " + q(f.getName())+";\n");
				worklist.add(sets.get(f));
			}
			if (ret != null) {
				w.write(q("return")+" [shape=box];\n");
				w.write(q(ret.toString()) + " -> " + q("return")+";\n");
				worklist.add(ret);
			}
			while (!worklist.isEmpty()) {
				Expr e = worklist.pop();
				for (Expr d : e.deps()) {
					w.write(q(d.toString()) + " -> " + q(e.toString())+";\n");
					worklist.add(d);
				}
			}
			w.write("}");
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}
	private static String q(String s) {
		return "\"" + s + "\"";
	}
}
