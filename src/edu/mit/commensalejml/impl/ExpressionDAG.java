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

import com.google.common.collect.BiMap;
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
