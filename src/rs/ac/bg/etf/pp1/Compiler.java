package rs.ac.bg.etf.pp1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

import java_cup.runtime.Symbol;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp1.util.Log4JUtils;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class Compiler {

	/* tip bool ne postoji u biblioteci tabele simbola, pa se dodaje ovde */
	public static final Struct boolType = new Struct(Struct.Bool);

	static {
		DOMConfigurator.configure(Log4JUtils.instance().findLoggerConfigFile());
		Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());
	}

	public static void tsdump() {
		Tab.dump();
	}

	public static void main(String[] args) throws Exception {

		Logger log = Logger.getLogger(Compiler.class);

		if (args.length < 1) {
			System.err.println("Upotreba: Compiler <ulazni fajl> [izlazni obj fajl]");
			return;
		}

		File sourceCode = new File(args[0]);
		if (!sourceCode.exists() || !sourceCode.canRead()) {
			System.err.println("Ne mogu da procitam ulazni fajl: " + sourceCode.getAbsolutePath());
			return;
		}

		log.info("Kompajliram izvorni fajl: " + sourceCode.getAbsolutePath());

		Reader br = null;
		try {
			br = new BufferedReader(new FileReader(sourceCode));
			Yylex lexer = new Yylex(br);
			MJParser p = new MJParser(lexer);

			/* Sintaksna analiza */
			Symbol s = p.parse();

			if (p.errorDetected) {
				System.err.println("Parsiranje NIJE uspesno zavrseno!");
				return;
			}

			Program prog = (Program) (s.value);

			/* Ispis apstraktnog sintaksnog stabla */
			System.out.println(prog.toString(""));
			System.out.println("===================================================");

			/* Inicijalizacija tabele simbola */
			Tab.init();
			Tab.currentScope().addToLocals(new Obj(Obj.Type, "bool", boolType));

			/* Semanticka analiza */
			SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer();
			prog.traverseBottomUp(semanticAnalyzer);

			/* Ispis sadrzaja tabele simbola */
			tsdump();

			if (semanticAnalyzer.passed()) {
				System.out.println("Parsiranje i semanticka analiza uspesno zavrseni!");
			} else {
				System.err.println("Semanticka analiza NIJE uspesno zavrsena!");
			}

		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					log.error(e.getMessage(), e);
				}
			}
		}
	}
}