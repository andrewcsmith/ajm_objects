package ajm;

import org.apache.bsf.BSFException;

import ajm.util.RubyEvaluator;

import com.cycling74.max.Atom;
import com.cycling74.max.MaxObject;

/**
 * Embeds a Ruby evaluator in a Max object
 * 
 * @author Adam Murray (dev@compusition.com)
 * 
 */
public class ruby extends AbstractMaxObject {

	boolean verbose = false;
	int evaloutlet = 0;

	private RubyEvaluator ruby;
	private AbstractMaxObject thisObj = this;

	/**
	 * The Constructor
	 * 
	 * @param args
	 *            1 optional integer arg specifies the number of outlets
	 * 
	 * @throws BSFException
	 *             if a problem occurs evaluating the Ruby initialization code
	 */
	public ruby(Atom[] args) throws BSFException {
		int outlets = 1;
		if (args.length > 0 && args[0].isInt() && args[0].getInt() > 1) {
			outlets = args[0].getInt();
		}
		declareIO(1, outlets);
		createInfoOutlet(false);

		declareAttribute("verbose", "getverbose", "verbose");
		declareAttribute("evaloutlet", "getevaloutlet", "evaloutlet");

		ruby = new RubyEvaluator();
		if (getAttrBool("verbose")) {
			ruby.setVerboseOut(System.out);
		}
		ruby.declareBean("MaxObject", this, MaxObject.class);
		ruby.declareBean("Utils", new Utils(), Utils.class);

		// TODO: it would be much nicer to load this from an init.rb script:
		CodeBuilder code = new CodeBuilder();

		code.line("def puts str");
		code.line("  $Utils.puts str");
		code.line("end");

		code.line("def print str");
		code.line("  $Utils.print str");
		code.line("end");

		code.line("def flush");
		code.line("  $Utils.flush");
		code.line("end");

		code.line("def atom obj");
		code.line("  if obj==nil");
		code.line("    $Utils.emptyAtomArray");
		code.line("  else");
		code.line("    $Utils.atom obj");
		code.line("  end");
		code.line("end");

		code.line("def flush");
		code.line("  $Utils.flush");
		code.line("end");

		code.line("def error str");
		code.line("  $Utils.error str");
		code.line("end");

		code.line("def outlet n, *params");
		code.line("  $Utils.outlet n, params");
		code.line("end");

		ruby.eval(code.toString());
	}

	/*
	 * protected MaxQelem getInitializer() { return new MaxQelem(new Executable() { public void execute() { } }); }
	 */

	public int getevaloutlet() {
		return evaloutlet;
	}

	public void evaloutlet(int evaloutlet) {
		if (evaloutlet >= getNumOutlets()) {
			err("Invalid evaloutlet " + evaloutlet);
		}
		else {
			this.evaloutlet = evaloutlet;
		}
	}

	public boolean getverbose() {
		return verbose;
	}

	public void verbose(boolean verbose) {
		this.verbose = verbose;
		ruby.setVerboseOut(verbose ? System.out : null);
	}

	public void list(Atom[] args) {
		anything(null, args);
	}

	public void anything(String msg, Atom[] args) {
		String input = anythingToString(msg, args);
		try {
			Atom[] value = ruby.evalToAtoms(input);
			if (evaloutlet >= 0) {
				outlet(evaloutlet, value);
			}
		}
		catch (BSFException e) {
			err("could not evaluate: " + input);
		}
	}

	private String anythingToString(String msg, Atom[] args) {
		StringBuffer input = new StringBuffer();
		if (msg != null) {
			input.append(msg).append(" ");
		}
		for (int i = 0; i < args.length; i++) {
			if (i > 0) {
				input.append(" ");
			}
			input.append(args[i]);
		}
		return input.toString();
	}

	private class CodeBuilder {
		private StringBuilder code = new StringBuilder();

		public void line(String s) {
			code.append(s).append("\n");
		}

		public String toString() {
			return code.toString();
		}
	}

	private class Utils {
		// JRuby has problems calling some Max Java methods, so I go back into Java-land to do it

		public Object atom(Object o) {
			Atom[] atoms = ruby.toAtoms(o);
			if (atoms.length == 1) {
				return atoms[0];
			}
			else {
				return atoms;
			}
		}

		public Atom[] emptyAtomArray() {
			return Atom.emptyArray;
		}

		public void puts(Object o) {
			System.out.println(o.toString());
		}

		public void print(Object o) {
			System.out.print(o.toString());
		}

		public void error(Object o) {
			thisObj.err(o.toString());
		}

		public void flush() {
			System.out.println();
		}

		public void outlet(int outletIdx, Object output) {
			if (outletIdx >= thisObj.getNumOutlets()) {
				err("Invalid outlet index " + outletIdx);
			}
			else {
				thisObj.outlet(outletIdx, ruby.toAtoms(output));
			}
		}
	}
}
