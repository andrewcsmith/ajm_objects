package ajm.rubysupport;

/*
Copyright (c) 2008, Adam Murray (adam@compusition.com). All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, 
this list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.regex.Pattern;

import org.apache.bsf.BSFException;
import org.jruby.RubyArray;
import org.jruby.RubyHash;

import ajm.util.LineBuilder;

import com.cycling74.max.Atom;
import com.cycling74.max.MaxObject;
import com.cycling74.max.MaxSystem;

/**
 * The bridge between Max and Ruby.
 * 
 * @version 0.85
 * @author Adam Murray (adam@compusition.com)
 */
public class MaxRubyEvaluator extends RubyEvaluator {

	public static final String NIL = "nil";

	public static final String PROP_JRUBY_HOME = "jruby.home";

	private LineBuilder code = new LineBuilder();

	private PrintStream verboseOut;

	private final MaxObject maxObj;

	private boolean initialized = false;

	private Pattern OMIT_PATHS = Pattern.compile(".*/\\.svn/.*");

	public MaxRubyEvaluator(MaxObject maxObj) {
		this(maxObj, "__" + Integer.toHexString(maxObj.hashCode()));
	}

	public MaxRubyEvaluator(MaxObject maxObj, String context) {
		System.out.println("Context = " + context);
		this.maxObj = maxObj;
	}

	public PrintStream getVerboseOut() {
		return verboseOut;
	}

	public void setVerboseOut(PrintStream verboseOut) {
		this.verboseOut = verboseOut;
	}

	/**
	 * @return an Atom or Atom[], it's up to the calling code to check the type
	 */
	public Object eval(CharSequence rubyCode) throws BSFException {
		if (!initialized) {
			init();
		}
		return toAtoms(super.eval(rubyCode));
	}

	/**
	 * Less efficient version of eval will always return an Atom[]
	 */
	public Atom[] evalToAtoms(CharSequence rubyCode) throws BSFException {
		Object o = eval(rubyCode);
		if (o instanceof Atom[]) {
			return (Atom[]) o;
		}
		else {
			return new Atom[] { (Atom) o };
		}
	}

	private void addPath(String path) {
		code.line("$: << '" + path.replace("'", "\\'") + "'");
	}

	public void init() {
		init(null);
	}

	public void init(String script) {
		super.init();

		if (System.getProperty(PROP_JRUBY_HOME) == null) {
			String pathToJRuby = MaxSystem.locateFile("jruby.jar");
			if (pathToJRuby != null) {
				File jRubyDir = new File(pathToJRuby).getParentFile();
				// Set jruby.home to the Max installation's java directory, where it will look for lib/ruby
				System.setProperty(PROP_JRUBY_HOME, jRubyDir.getParent());
			}
			else {
				MaxSystem.error("jruby.jar not found! Maybe it was not installed correctly?");
			}
		}

		if (code.isEmpty()) {
			// Setup the path:
			String patcherPath = maxObj.getParentPatcher().getPath();
			if (patcherPath != null) {
				// Add the patch's folder and subfolders
				addPath(patcherPath);
				File ppath = new File(patcherPath);
				for (File file : ppath.listFiles()) {
					if (file.isDirectory()) {
						String path = file.getAbsolutePath();
						if (!OMIT_PATHS.matcher(path).matches()) {
							addPath(path);
						}
					}
				}
			}

			for (String path : MaxSystem.getSearchPath()) {
				if (!OMIT_PATHS.matcher(path).matches()) {
					addPath(path);
				}
			}

			// Setup the default functions:
			code.line("def puts(*params)");
			code.line("  $Utils.puts(params)");
			code.line("end");

			code.line("def print(*params)");
			code.line("  $Utils.print(params)");
			code.line("end");

			code.line("def flush");
			code.line("  $Utils.flush");
			code.line("end");

			code.line("def atom(obj)");
			code.line("  if obj");
			code.line("    $Utils.atom(obj)");
			code.line("  else");
			code.line("    $Utils.emptyAtomArray");
			code.line("  end");
			code.line("end");

			code.line("def error(*params)");
			code.line("  $Utils.error(params)");
			code.line("end");

			code.line("def outlet(n, *params)");
			code.line("  $Utils.outlet(n, params)");
			code.line("end");

			for (int i = 0; i < 10; i++) {
				// TODO: better handled with metaprogramming?
				code.line("def out" + i + "(*params)");
				code.line("  $Utils.outlet(" + i + ", params)");
				code.line("end");
			}

			// Placeholders for Max hooks:
			code.line("def bang");
			code.line("  'bang'");
			code.line("end");

			code.line("def list(array)");
			code.line("  array");
			code.line("end");
		}

		try {
			declareBean("MaxObject", maxObj, maxObj.getClass());
			declareBean("Utils", new Utils(), Utils.class);
			initialized = true;

			eval(code);

			if (script != null) {
				eval(script);
			}
		}
		catch (BSFException e) {
			throw new RuntimeException(e);
		}

	}

	/**
	 * Converts the result of a Ruby evaluation into Max data types (Atoms)
	 * 
	 * @param obj -
	 *            A Ruby value
	 * @return an Atom or an Atom[]. The calling code needs to figure out what type this is and handle it appropriately
	 */
	public Object toAtoms(Object obj) {

		/*
		if (obj != null) {
			System.out.println(obj.getClass().getName());
		}
		*/

		if (obj == null) {
			return Atom.newAtom("nil");
		}
		else if (obj instanceof Atom || obj instanceof Atom[]) {
			return obj;
		}

		else if (obj instanceof Double || obj instanceof Float) {
			return Atom.newAtom(((Number) obj).doubleValue());
		}

		else if (obj instanceof Long || obj instanceof Integer) {
			return Atom.newAtom(((Number) obj).longValue());
		}

		else if (obj instanceof Boolean) {
			return Atom.newAtom(((Boolean) obj).booleanValue());
		}

		else if (obj instanceof RubyArray) {
			RubyArray array = (RubyArray) obj;
			if (array.size() == 1) {
				return toAtoms(array.get(0));
			}
			else {
				Atom[] out = new Atom[array.size()];
				for (int i = 0; i < array.size(); i++) {
					Object val = toAtoms(array.get(i));
					if (val instanceof Atom) {
						out[i] = (Atom) val;
					}
					else {
						Atom[] vals = (Atom[]) val;
						if (vals.length == 1) {
							out[i] = vals[0];
						}
						else {
							// TODO: it is probably ok if we only have one level of nesting (an array inside an array)
							// to return a space separated string e.g. [1, [2,3], 4] => 1 "2 3" 4
							// Further nesting should insert array brackets: [1, [2, [3,4]], 5] => 1 "2 [3,4]" 5
							if (verboseOut != null) {
								verboseOut.println("Ruby: coerced a nested Array to String");
							}
							out[i] = Atom.newAtom(Arrays.toString(vals));
						}
					}

				}
				return out;
			}
		}

		else if (obj instanceof RubyHash) {
			if (verboseOut != null) {
				verboseOut.println("Ruby: coerced a Hash to String");
			}
			RubyHash hash = (RubyHash) obj;
			StringBuilder s = new StringBuilder();
			for (Object key : hash.keySet()) {
				if (s.length() > 0) {
					s.append(", ");
				}
				s.append(toArrayString(toAtoms(key)));
				s.append("=>");
				s.append(toArrayString(toAtoms(hash.get(key))));
			}
			s.insert(0, "{");
			s.append("}");
			return new Atom[] { Atom.newAtom(s.toString()) };
		}

		else {
			if (verboseOut != null && !(obj instanceof String)) {
				verboseOut.println("Ruby: coerced type " + obj.getClass().getName() + " to String");
			}
			return new Atom[] { Atom.newAtom(obj.toString()) };
		}

	}

	private String toArrayString(Object o) {
		if (o instanceof Atom[]) {
			Atom[] atoms = (Atom[]) o;
			if (atoms.length == 1) {
				return atoms[0].toString();
			}
			else {
				return Arrays.toString(atoms);
			}
		}
		else {
			return o.toString();
		}
	}

	private class Utils {
		// JRuby has problems calling some Max Java methods, so I go back into Java-land to do it

		public Object atom(Object o) {
			return toAtoms(o);
		}

		public Atom[] emptyAtomArray() {
			return Atom.emptyArray;
		}

		public void puts(Object o) {
			Object atom = toAtoms(o);
			if (atom instanceof Atom[]) {
				for (Atom a : (Atom[]) atom) {
					System.out.println(a);
				}
			}
			else {
				System.out.println(o);
			}
		}

		public void print(Object o) {
			Object atom = toAtoms(o);
			if (atom instanceof Atom[]) {
				for (Atom a : (Atom[]) atom) {
					System.out.print(a);
				}
			}
			else {
				System.out.print(o);
			}
		}

		public void error(Object o) {
			Object atom = toAtoms(o);
			if (atom instanceof Atom[]) {
				for (Atom a : (Atom[]) atom) {
					MaxSystem.error(a.toString());
				}
			}
			else {
				System.out.println(atom.toString());
			}
		}

		public void flush() {
			System.out.println();
		}

		public void outlet(int outletIdx, Object output) {
			if (outletIdx >= maxObj.getNumOutlets()) {
				MaxSystem.error("Invalid outlet index " + outletIdx);
			}
			else {
				Object atoms = toAtoms(output);
				if (atoms instanceof Atom[]) {
					maxObj.outlet(outletIdx, (Atom[]) atoms);
				}
				else {
					maxObj.outlet(outletIdx, (Atom) atoms);
				}
			}
		}
	}
}