package rs.ac.bg.etf.pp1;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class SemanticAnalyzer extends VisitorAdaptor {

	private boolean errorDetected = false;
	Logger log = Logger.getLogger(getClass());

	private Obj currentProgram;
	private Struct currentType;
	private int constant;
	private Struct constantType;
	private Struct boolType = Compiler.boolType;
	private Obj mainMethod;
	private Obj currentMethod;
	private boolean returnHappened;

	/* LOG MESSAGES */

	public void report_error(String message, SyntaxNode info) {
		errorDetected = true;
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0 : info.getLine();
		if (line != 0)
			msg.append(" na liniji ").append(line);
		log.error(msg.toString());
	}

	public void report_info(String message, SyntaxNode info) {
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0 : info.getLine();
		if (line != 0)
			msg.append(" na liniji ").append(line);
		log.info(msg.toString());
	}

	public boolean passed() {
		return !errorDetected;
	}

	/* POMOCNE METODE */

	private boolean isBuiltIn(Struct s) {
		return s.equals(Tab.intType) || s.equals(Tab.charType) || s.equals(boolType);
	}

	private boolean isAssignable(int kind) {
		return kind == Obj.Var || kind == Obj.Elem || kind == Obj.Fld;
	}

	/* provera da li je cvor unutar for petlje - penjanje uz roditelje */
	private boolean insideLoop(SyntaxNode node) {
		for (SyntaxNode p = node.getParent(); p != null; p = p.getParent())
			if (p instanceof SingleStatement_for)
				return true;
		return false;
	}

	/* prikupljanje tipova stvarnih argumenata, s leva na desno */
	private void collectActPars(SyntaxNode node, List<Struct> list) {
		if (node instanceof ActPars_rec) {
			ActPars_rec n = (ActPars_rec) node;
			list.add(n.getActParsOne().struct);
			collectActPars(n.getActParsMore(), list);
		} else if (node instanceof ActParsMore_comma) {
			ActParsMore_comma n = (ActParsMore_comma) node;
			list.add(n.getActParsOne().struct);
			collectActPars(n.getActParsMore(), list);
		}
	}

	private void checkCall(Obj methObj, SyntaxNode actPars, SyntaxNode node) {
		if (methObj.getKind() != Obj.Meth) {
			report_error("Ime " + methObj.getName() + " ne oznacava metodu", node);
			return;
		}
		List<Struct> actual = new ArrayList<>();
		collectActPars(actPars, actual);

		List<Obj> formal = new ArrayList<>();
		
		int numPar = methObj.getLevel();
		int cnt = 0;
		for (Obj o : methObj.getLocalSymbols()) {
			if (cnt >= numPar)
				break;
			formal.add(o);
			cnt++;
		}

		if (actual.size() != formal.size()) {
			report_error("Broj argumenata u pozivu metode " + methObj.getName()
					+ " ne odgovara broju formalnih parametara", node);
			return;
		}
		for (int i = 0; i < actual.size(); i++) {
			if (!actual.get(i).assignableTo(formal.get(i).getType())) {
				report_error("Tip argumenta " + (i + 1) + " u pozivu metode "
						+ methObj.getName() + " nije kompatibilan", node);
			}
		}
	}

	/* PROGRAM */

	@Override
	public void visit(ProgramName programName) {
		currentProgram = Tab.insert(Obj.Prog, programName.getI1(), Tab.noType);
		Tab.openScope();
	}

	@Override
	public void visit(Program program) {
		Tab.chainLocalSymbols(currentProgram);
		Tab.closeScope();
		currentProgram = null;

		if (mainMethod == null)
			report_error("Program nema main metodu", program);
		else if (mainMethod.getLevel() > 0)
			report_error("Metoda main ne sme imati parametre", program);
	}

	/* TYPE */

	@Override
	public void visit(Type type) {
		Obj typeObj = Tab.find(type.getI1());
		if (typeObj == Tab.noObj) {
			report_error("Nepostojeci tip podatka: " + type.getI1(), type);
			currentType = Tab.noType;
		} else if (typeObj.getKind() != Obj.Type) {
			report_error("Ime " + type.getI1() + " ne oznacava tip podatka", type);
			currentType = Tab.noType;
		} else {
			currentType = typeObj.getType();
		}
	}

	/* CONST DECLARATIONS */

	@Override
	public void visit(ConstDeclOne constDeclOne) {
		if (Tab.currentScope().findSymbol(constDeclOne.getI1()) != null) {
			report_error("Ime " + constDeclOne.getI1() + " je vec deklarisano u ovom opsegu", constDeclOne);
			return;
		}
		if (!constantType.equals(currentType)) {
			report_error("Tip konstante " + constDeclOne.getI1() + " ne odgovara deklarisanom tipu", constDeclOne);
			return;
		}
		Obj conObj = Tab.insert(Obj.Con, constDeclOne.getI1(), currentType);
		conObj.setAdr(constant);
	}

	@Override
	public void visit(Constant_n constant_n) {
		constant = constant_n.getN1();
		constantType = Tab.intType;
	}

	@Override
	public void visit(Constant_c constant_c) {
		constant = constant_c.getC1();
		constantType = Tab.charType;
	}

	@Override
	public void visit(Constant_b constant_b) {
		constant = constant_b.getB1();
		constantType = boolType;
	}

	/* VAR DECLARATIONS */

	@Override
	public void visit(VarDeclOne_var varDeclOne_var) {
		insertVar(varDeclOne_var.getI1(), currentType, varDeclOne_var);
	}

	@Override
	public void visit(VarDeclOne_array varDeclOne_array) {
		insertVar(varDeclOne_array.getI1(), new Struct(Struct.Array, currentType), varDeclOne_array);
	}

	private void insertVar(String name, Struct type, SyntaxNode node) {
		if (Tab.currentScope().findSymbol(name) != null) {
			report_error("Ime " + name + " je vec deklarisano u ovom opsegu", node);
			return;
		}
		Tab.insert(Obj.Var, name, type);
	}

	/* METHOD DECLARATIONS */

	@Override
	public void visit(MethRetAndName_void methRetAndName_void) {
		openMethod(methRetAndName_void.getI1(), Tab.noType, methRetAndName_void);
	}

	@Override
	public void visit(MethRetAndName_type methRetAndName_type) {
		openMethod(methRetAndName_type.getI2(), currentType, methRetAndName_type);
	}

	private void openMethod(String name, Struct retType, SyntaxNode node) {
		if (Tab.currentScope().findSymbol(name) != null)
			report_error("Ime " + name + " je vec deklarisano u ovom opsegu", node);

		currentMethod = Tab.insert(Obj.Meth, name, retType);
		currentMethod.setLevel(0);
		returnHappened = false;
		Tab.openScope();

		if ("main".equals(name)) {
			mainMethod = currentMethod;
			if (retType != Tab.noType)
				report_error("Metoda main mora biti tipa void", node);
		}
	}

	@Override
	public void visit(MethodDecl_fp methodDecl_fp) {
		closeMethod(methodDecl_fp);
	}

	@Override
	public void visit(MethodDecl_nfp methodDecl_nfp) {
		closeMethod(methodDecl_nfp);
	}

	private void closeMethod(SyntaxNode node) {
		if (currentMethod.getType() != Tab.noType && !returnHappened)
			report_error("Metoda " + currentMethod.getName() + " nema return iskaz", node);

		Tab.chainLocalSymbols(currentMethod);
		Tab.closeScope();
		currentMethod = null;
		returnHappened = false;
	}

	/* FORMAL PARAMETERS */

	@Override
	public void visit(FormParsOne_var formParsOne_var) {
		insertFormPar(formParsOne_var.getI2(), currentType, formParsOne_var);
	}

	@Override
	public void visit(FormParsOne_array formParsOne_array) {
		insertFormPar(formParsOne_array.getI2(), new Struct(Struct.Array, currentType), formParsOne_array);
	}

	private void insertFormPar(String name, Struct type, SyntaxNode node) {
		if (currentMethod == null) {
			report_error("Formalni parametar van metode: " + name, node);
			return;
		}
		if (Tab.currentScope().findSymbol(name) != null) {
			report_error("Formalni parametar " + name + " je vec deklarisan", node);
			return;
		}
		Obj parObj = Tab.insert(Obj.Var, name, type);
		parObj.setFpPos(1);
		currentMethod.setLevel(currentMethod.getLevel() + 1);
	}

	/* DESIGNATOR */

	@Override
	public void visit(Designator_var designator_var) {
		Obj varObj = Tab.find(designator_var.getI1());
		if (varObj == Tab.noObj) {
			report_error("Ime " + designator_var.getI1() + " nije deklarisano", designator_var);
			designator_var.obj = Tab.noObj;
			return;
		}
		designator_var.obj = varObj;
		report_info("Pretraga na " + designator_var.getLine() + "(" + varObj.getName()
				+ "), nadjeno " + objToString(varObj), null);
	}

	private String objToString(Obj o) {
		String kind;
		switch (o.getKind()) {
		case Obj.Con:  kind = "Con";  break;
		case Obj.Var:  kind = "Var";  break;
		case Obj.Meth: kind = "Meth"; break;
		case Obj.Type: kind = "Type"; break;
		case Obj.Fld:  kind = "Fld";  break;
		case Obj.Elem: kind = "Elem"; break;
		case Obj.Prog: kind = "Prog"; break;
		default:       kind = "???";  break;
		}
		return kind + " " + o.getName() + ": " + structToString(o.getType())
				+ ", " + o.getAdr() + ", " + o.getLevel();
	}

	private String structToString(Struct s) {
		switch (s.getKind()) {
		case Struct.None:  return "notype";
		case Struct.Int:   return "int";
		case Struct.Char:  return "char";
		case Struct.Bool:  return "bool";
		case Struct.Array: return "Arr of " + structToString(s.getElemType());
		default:           return "Class";
		}
	}

	@Override
	public void visit(DesignatorArr designatorArr) {
		designatorArr.obj = designatorArr.getDesignator().obj;
	}

	@Override
	public void visit(Designator_arr designator_arr) {
		Obj arrObj = designator_arr.getDesignatorArr().obj;

		if (arrObj == Tab.noObj) {
			designator_arr.obj = Tab.noObj;
			return;
		}
		if (arrObj.getType().getKind() != Struct.Array) {
			report_error("Ime " + arrObj.getName() + " ne oznacava niz", designator_arr);
			designator_arr.obj = Tab.noObj;
			return;
		}
		if (!designator_arr.getExpr().struct.equals(Tab.intType)) {
			report_error("Indeks niza mora biti tipa int", designator_arr);
			designator_arr.obj = Tab.noObj;
			return;
		}
		designator_arr.obj = new Obj(Obj.Elem, arrObj.getName() + "[$]",
				arrObj.getType().getElemType());
	}

	@Override
	public void visit(Designator_len designator_len) {
		Obj arrObj = designator_len.getDesignator().obj;

		if (arrObj == Tab.noObj) {
			designator_len.obj = Tab.noObj;
			return;
		}
		if (arrObj.getType().getKind() != Struct.Array) {
			report_error("Polje length se moze koristiti samo nad nizom", designator_len);
			designator_len.obj = Tab.noObj;
			return;
		}
		designator_len.obj = new Obj(Obj.Elem, arrObj.getName() + ".length", Tab.intType);
	}

	/* FACTOR */

	@Override
	public void visit(FactorSub_n factorSub_n) {
		factorSub_n.struct = Tab.intType;
	}

	@Override
	public void visit(FactorSub_c factorSub_c) {
		factorSub_c.struct = Tab.charType;
	}

	@Override
	public void visit(FactorSub_b factorSub_b) {
		factorSub_b.struct = boolType;
	}

	@Override
	public void visit(FactorSub_var factorSub_var) {
		factorSub_var.struct = factorSub_var.getDesignator().obj.getType();
	}

	@Override
	public void visit(FactorSub_meth factorSub_meth) {
		Obj methObj = factorSub_meth.getDesignator().obj;
		checkCall(methObj, factorSub_meth.getActPars(), factorSub_meth);
		factorSub_meth.struct = (methObj.getKind() == Obj.Meth) ? methObj.getType() : Tab.noType;
	}

	@Override
	public void visit(FactorSub_new_array factorSub_new_array) {
		if (!factorSub_new_array.getExpr().struct.equals(Tab.intType)) {
			report_error("Velicina niza mora biti tipa int", factorSub_new_array);
			factorSub_new_array.struct = Tab.noType;
		} else {
			factorSub_new_array.struct = new Struct(Struct.Array, currentType);
		}
	}

	@Override
	public void visit(FactorSub_new_record factorSub_new_record) {
		report_error("Pravljenje objekata klasa nije podrzano", factorSub_new_record);
		factorSub_new_record.struct = Tab.noType;
	}

	@Override
	public void visit(FactorSub_expr factorSub_expr) {
		factorSub_expr.struct = factorSub_expr.getExpr().struct;
	}

	@Override
	public void visit(Factor_one factor_one) {
		factor_one.struct = factor_one.getFactorSub().struct;
	}

	@Override
	public void visit(Factor_unary factor_unary) {
		if (factor_unary.getFactorSub().struct.equals(Tab.intType)) {
			factor_unary.struct = Tab.intType;
		} else {
			report_error("Unarni minus se moze primeniti samo na int", factor_unary);
			factor_unary.struct = Tab.noType;
		}
	}

	/* EXPR */

	@Override
	public void visit(MulopFactorList_factor mulopFactorList_factor) {
		mulopFactorList_factor.struct = mulopFactorList_factor.getFactor().struct;
	}

	@Override
	public void visit(MulopFactorList_mul mulopFactorList_mul) {
		Struct left = mulopFactorList_mul.getMulopFactorList().struct;
		Struct right = mulopFactorList_mul.getFactor().struct;
		if (left.equals(Tab.intType) && right.equals(Tab.intType)) {
			mulopFactorList_mul.struct = Tab.intType;
		} else {
			report_error("Operandi mnozenja/deljenja moraju biti tipa int", mulopFactorList_mul);
			mulopFactorList_mul.struct = Tab.noType;
		}
	}

	@Override
	public void visit(Term term) {
		term.struct = term.getMulopFactorList().struct;
	}

	@Override
	public void visit(AddopTermList_term addopTermList_term) {
		addopTermList_term.struct = addopTermList_term.getTerm().struct;
	}

	@Override
	public void visit(AddopTermList_add addopTermList_add) {
		Struct left = addopTermList_add.getAddopTermList().struct;
		Struct right = addopTermList_add.getTerm().struct;
		if (left.equals(Tab.intType) && right.equals(Tab.intType)) {
			addopTermList_add.struct = Tab.intType;
		} else {
			report_error("Operandi sabiranja/oduzimanja moraju biti tipa int", addopTermList_add);
			addopTermList_add.struct = Tab.noType;
		}
	}

	@Override
	public void visit(Expr_term expr_term) {
		expr_term.struct = expr_term.getAddopTermList().struct;
	}

	@Override
	public void visit(Expr_ternary expr_ternary) {
		Struct t1 = expr_ternary.getExpr().struct;
		Struct t2 = expr_ternary.getExpr1().struct;
		if (!t1.equals(t2)) {
			report_error("Grane ternarnog operatora moraju biti istog tipa", expr_ternary);
			expr_ternary.struct = Tab.noType;
		} else {
			expr_ternary.struct = t1;
		}
	}

	/* CONDITION */

	@Override
	public void visit(CondFact_one condFact_one) {
		if (!condFact_one.getAddopTermList().struct.equals(boolType)) {
			report_error("Uslovni izraz mora biti tipa bool", condFact_one);
			condFact_one.struct = Tab.noType;
		} else {
			condFact_one.struct = boolType;
		}
	}

	@Override
	public void visit(CondFact_relop condFact_relop) {
		Struct left = condFact_relop.getAddopTermList().struct;
		Struct right = condFact_relop.getAddopTermList1().struct;

		if (!left.compatibleWith(right)) {
			report_error("Operandi relacionog operatora nisu kompatibilni", condFact_relop);
			condFact_relop.struct = Tab.noType;
			return;
		}
		if (left.isRefType() || right.isRefType()) {
			if (!(condFact_relop.getRelop() instanceof Relop_eq)
					&& !(condFact_relop.getRelop() instanceof Relop_neq)) {
				report_error("Uz nizove se mogu koristiti samo == i !=", condFact_relop);
				condFact_relop.struct = Tab.noType;
				return;
			}
		}
		condFact_relop.struct = boolType;
	}

	@Override
	public void visit(CondFactList_cf condFactList_cf) {
		condFactList_cf.struct = condFactList_cf.getCondFact().struct;
	}

	@Override
	public void visit(CondFactList_and condFactList_and) {
		Struct left = condFactList_and.getCondFactList().struct;
		Struct right = condFactList_and.getCondFact().struct;
		if (left.equals(boolType) && right.equals(boolType)) {
			condFactList_and.struct = boolType;
		} else {
			report_error("Operandi operatora && moraju biti tipa bool", condFactList_and);
			condFactList_and.struct = Tab.noType;
		}
	}

	@Override
	public void visit(CondTerm condTerm) {
		condTerm.struct = condTerm.getCondFactList().struct;
	}

	@Override
	public void visit(CondTermList_ct condTermList_ct) {
		condTermList_ct.struct = condTermList_ct.getCondTerm().struct;
	}

	@Override
	public void visit(CondTermList_or condTermList_or) {
		Struct left = condTermList_or.getCondTermList().struct;
		Struct right = condTermList_or.getCondTerm().struct;
		if (left.equals(boolType) && right.equals(boolType)) {
			condTermList_or.struct = boolType;
		} else {
			report_error("Operandi operatora || moraju biti tipa bool", condTermList_or);
			condTermList_or.struct = Tab.noType;
		}
	}

	@Override
	public void visit(Condition condition) {
		condition.struct = condition.getCondTermList().struct;
		if (!condition.struct.equals(boolType))
			report_error("Uslov mora biti tipa bool", condition);
	}

	/* ACT PARS */

	@Override
	public void visit(ActParsOne actParsOne) {
		actParsOne.struct = actParsOne.getExpr().struct;
	}

	/* DESIGNATOR STATEMENTS */

	@Override
	public void visit(DesignatorStatement_assign designatorStatement_assign) {
		Obj dest = designatorStatement_assign.getDesignator().obj;
		if (!isAssignable(dest.getKind())) {
			report_error("Dodela nije moguca: " + dest.getName()
					+ " ne oznacava promenljivu, element niza ili polje", designatorStatement_assign);
			return;
		}
		if (!designatorStatement_assign.getExpr().struct.assignableTo(dest.getType()))
			report_error("Tip izraza nije kompatibilan pri dodeli sa tipom promenljive "
					+ dest.getName(), designatorStatement_assign);
	}

	@Override
	public void visit(DesignatorStatement_inc designatorStatement_inc) {
		checkIncDec(designatorStatement_inc.getDesignator().obj, "Inkrement", designatorStatement_inc);
	}

	@Override
	public void visit(DesignatorStatement_dec designatorStatement_dec) {
		checkIncDec(designatorStatement_dec.getDesignator().obj, "Dekrement", designatorStatement_dec);
	}

	private void checkIncDec(Obj obj, String op, SyntaxNode node) {
		if (!isAssignable(obj.getKind()))
			report_error(op + " nije moguc nad " + obj.getName(), node);
		else if (!obj.getType().equals(Tab.intType))
			report_error(op + " je moguc samo nad int vrednoscu: " + obj.getName(), node);
	}

	@Override
	public void visit(DesignatorStatement_meth designatorStatement_meth) {
		checkCall(designatorStatement_meth.getDesignator().obj,
				designatorStatement_meth.getActPars(), designatorStatement_meth);
	}

	/* STATEMENTS */

	@Override
	public void visit(SingleStatement_read singleStatement_read) {
		Obj obj = singleStatement_read.getDesignator().obj;
		if (!isAssignable(obj.getKind()))
			report_error("read zahteva promenljivu, element niza ili polje", singleStatement_read);
		else if (!isBuiltIn(obj.getType()))
			report_error("read zahteva int, char ili bool", singleStatement_read);
	}

	@Override
	public void visit(SingleStatement_print1 singleStatement_print1) {
		if (!isBuiltIn(singleStatement_print1.getExpr().struct))
			report_error("print zahteva int, char ili bool", singleStatement_print1);
	}

	@Override
	public void visit(SingleStatement_print2 singleStatement_print2) {
		if (!isBuiltIn(singleStatement_print2.getExpr().struct))
			report_error("print zahteva int, char ili bool", singleStatement_print2);
	}

	@Override
	public void visit(SingleStatement_return1 singleStatement_return1) {
		returnHappened = true;
		if (currentMethod == null) {
			report_error("Iskaz return van tela metode", singleStatement_return1);
			return;
		}
		if (currentMethod.getType() != Tab.noType)
			report_error("Metoda " + currentMethod.getName() + " mora vratiti vrednost",
					singleStatement_return1);
	}

	@Override
	public void visit(SingleStatement_return2 singleStatement_return2) {
		returnHappened = true;
		if (currentMethod == null) {
			report_error("Iskaz return van tela metode", singleStatement_return2);
			return;
		}
		if (currentMethod.getType() == Tab.noType) {
			report_error("void metoda " + currentMethod.getName() + " ne sme vracati vrednost",
					singleStatement_return2);
		} else if (!singleStatement_return2.getExpr().struct.equals(currentMethod.getType())) {
			report_error("Tip izraza u return ne odgovara povratnom tipu metode "
					+ currentMethod.getName(), singleStatement_return2);
		}
	}

	@Override
	public void visit(SingleStatement_break singleStatement_break) {
		if (!insideLoop(singleStatement_break))
			report_error("Iskaz break mora biti unutar for petlje", singleStatement_break);
	}

	@Override
	public void visit(SingleStatement_continue singleStatement_continue) {
		if (!insideLoop(singleStatement_continue))
			report_error("Iskaz continue mora biti unutar for petlje", singleStatement_continue);
	}

	/* FIND ANY */

	@Override
	public void visit(SingleStatement_findAny stmt) {
		Obj dest = stmt.getDesignator().obj;
		Obj src = stmt.getDesignator1().obj;

		if (!isAssignable(dest.getKind()) || !dest.getType().equals(boolType)) {
			report_error("Rezultat findAny se mora dodeliti promenljivoj tipa bool", stmt);
			return;
		}
		if (src.getType().getKind() != Struct.Array) {
			report_error("findAny se moze primeniti samo na niz", stmt);
			return;
		}
		Struct elem = src.getType().getElemType();
		if (!isBuiltIn(elem)) {
			report_error("findAny zahteva niz ugradjenog tipa", stmt);
			return;
		}
		if (!stmt.getExpr().struct.equals(elem))
			report_error("Tip izraza u findAny ne odgovara tipu elemenata niza", stmt);
	}

	/* MAP */

	@Override
	public void visit(SingleStatement_map stmt) {
		Obj dest = stmt.getDesignator().obj;
		Obj src = stmt.getDesignator1().obj;

		if (!isAssignable(dest.getKind()) || dest.getType().getKind() != Struct.Array) {
			report_error("Rezultat map se mora dodeliti nizu", stmt);
			return;
		}
		if (src.getType().getKind() != Struct.Array) {
			report_error("map se moze primeniti samo na niz", stmt);
			return;
		}
		Struct srcElem = src.getType().getElemType();
		if (!isBuiltIn(srcElem)) {
			report_error("map zahteva niz ugradjenog tipa", stmt);
			return;
		}

		Obj identObj = Tab.find(stmt.getI4());
		if (identObj == Tab.noObj) {
			report_error("Ime " + stmt.getI4() + " nije deklarisano", stmt);
			return;
		}
		if (identObj.getKind() != Obj.Var) {
			report_error("Ime " + stmt.getI4() + " mora biti promenljiva", stmt);
			return;
		}
		if (!identObj.getType().equals(srcElem)) {
			report_error("Promenljiva " + stmt.getI4() + " mora biti istog tipa kao elementi niza", stmt);
			return;
		}
		if (!stmt.getExpr().struct.assignableTo(dest.getType().getElemType()))
			report_error("Tip izraza u map ne odgovara tipu elemenata rezultujuceg niza", stmt);
	}
}