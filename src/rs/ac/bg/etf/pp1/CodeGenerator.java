package rs.ac.bg.etf.pp1;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class CodeGenerator extends VisitorAdaptor {

	private int mainPc = -1;
	private Obj currentMethod;

	/* pomocni globalni slotovi za findAny i map */
	private final int tmpVal;
	private final int tmpIdx;
	private final Map<SingleStatement_map, Obj> mapIdents;
	private final Map<SingleStatement_mapFrom, Obj> mapFromIdents;


	public CodeGenerator(int nVars, Map<SingleStatement_map, Obj> mapIdents, Map<SingleStatement_mapFrom, Obj> mapFromIdents) {
		this.tmpVal = nVars;
		this.tmpIdx = nVars + 1;
		this.mapIdents = mapIdents;
		this.mapFromIdents = mapFromIdents;
		initPredeclaredMethods();
	}

	public int getMainPc() {
		return mainPc;
	}

	private void initPredeclaredMethods() {
		Obj ordObj = Tab.find("ord");
		Obj chrObj = Tab.find("chr");
		ordObj.setAdr(Code.pc);
		chrObj.setAdr(Code.pc);
		Code.put(Code.enter);
		Code.put(1);
		Code.put(1);
		Code.put(Code.load_n);
		Code.put(Code.exit);
		Code.put(Code.return_);

		Obj lenObj = Tab.find("len");
		lenObj.setAdr(Code.pc);
		Code.put(Code.enter);
		Code.put(1);
		Code.put(1);
		Code.put(Code.load_n);
		Code.put(Code.arraylength);
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	private boolean isChar(Struct s) {
		return s != null && s.getKind() == Struct.Char;
	}

	private Obj elemObj(Struct elemType) {
		return new Obj(Obj.Elem, "$elem", elemType);
	}

	/* METODE */

	@Override
	public void visit(MethRetAndName_void node) {
		openMethod(node.obj, node.getI1());
	}

	@Override
	public void visit(MethRetAndName_type node) {
		openMethod(node.obj, node.getI2());
	}

	private void openMethod(Obj methObj, String name) {
		currentMethod = methObj;
		methObj.setAdr(Code.pc);
		if ("main".equals(name))
			mainPc = Code.pc;

		Code.put(Code.enter);
		Code.put(methObj.getLevel());
		Code.put(methObj.getLocalSymbols().size());
	}

	@Override
	public void visit(MethodDecl_fp node) {
		closeMethod();
	}

	@Override
	public void visit(MethodDecl_nfp node) {
		closeMethod();
	}

	private void closeMethod() {
		if (currentMethod.getType() != Tab.noType) {
			/* pad kroz kraj tela ne-void metode je runtime greska 1 */
			Code.put(Code.trap);
			Code.put(1);
		} else {
			Code.put(Code.exit);
			Code.put(Code.return_);
		}
		currentMethod = null;
	}

	/* DESIGNATOR */

	@Override
	public void visit(DesignatorArr node) {
		/* adresa niza mora na stek pre indeksa */
		Code.load(node.obj);
	}

	@Override
	public void visit(Designator_len node) {
		Code.load(node.getDesignator().obj);
		Code.put(Code.arraylength);
	}

	/* FACTOR */

	@Override
	public void visit(FactorSub_n node) {
		Code.loadConst(node.getN1());
	}

	@Override
	public void visit(FactorSub_c node) {
		Code.loadConst(node.getC1());
	}

	@Override
	public void visit(FactorSub_b node) {
		Code.loadConst(node.getB1());
	}

	@Override
	public void visit(FactorSub_var node) {
		/* Designator_len je vec ostavio duzinu na steku */
		if (node.getDesignator() instanceof Designator_len)
			return;
		Code.load(node.getDesignator().obj);
	}

	@Override
	public void visit(FactorSub_expr node) {
		/* vrednost je vec na steku */
	}

	@Override
	public void visit(FactorSub_new_array node) {
		Code.put(Code.newarray);
		Code.put(isChar(node.struct.getElemType()) ? 0 : 1);
	}

	@Override
	public void visit(FactorSub_meth node) {
		int offset = node.getDesignator().obj.getAdr() - Code.pc;
		Code.put(Code.call);
		Code.put2(offset);
	}

	@Override
	public void visit(Factor_unary node) {
		Code.put(Code.neg);
	}

	/* ARITMETIKA */

	@Override
	public void visit(AddopTermList_add node) {
		if (node.getAddop() instanceof Addop_plus)
			Code.put(Code.add);
		else
			Code.put(Code.sub);
	}

	@Override
	public void visit(MulopFactorList_mul node) {
		if (node.getMulop() instanceof Mulop_mul)
			Code.put(Code.mul);
		else if (node.getMulop() instanceof Mulop_div)
			Code.put(Code.div);
		else
			Code.put(Code.rem);
	}

	/* DESIGNATOR STATEMENTS */

	@Override
	public void visit(DesignatorStatement_assign node) {
		Code.store(node.getDesignator().obj);
	}
	
	@Override
	public void visit(DesignatorStatement_plusassign node) {
		Code.put(Code.add);
		Code.store(node.getDesignator().obj);
	}
	
	@Override
	public void visit(PlusAssignMark node) {
		DesignatorStatement_plusassign stmt = (DesignatorStatement_plusassign) node.getParent();
		Obj obj = stmt.getDesignator().obj;
		if (obj.getKind() == Obj.Elem)
			Code.put(Code.dup2);
		Code.load(obj);
	}

	@Override
	public void visit(DesignatorStatement_inc node) {
		incDec(node.getDesignator().obj, true);
	}

	@Override
	public void visit(DesignatorStatement_dec node) {
		incDec(node.getDesignator().obj, false);
	}

	private void incDec(Obj obj, boolean inc) {
		if (obj.getKind() == Obj.Elem)
			Code.put(Code.dup2);
		Code.load(obj);
		Code.loadConst(1);
		Code.put(inc ? Code.add : Code.sub);
		Code.store(obj);
	}

	@Override
	public void visit(DesignatorStatement_meth node) {
		Obj methObj = node.getDesignator().obj;
		int offset = methObj.getAdr() - Code.pc;
		Code.put(Code.call);
		Code.put2(offset);
		if (methObj.getType() != Tab.noType)
			Code.put(Code.pop);
	}

	/* ISKAZI */

	@Override
	public void visit(SingleStatement_print1 node) {
		Code.loadConst(isChar(node.getExpr().struct) ? 1 : 0);
		Code.put(isChar(node.getExpr().struct) ? Code.bprint : Code.print);
	}

	@Override
	public void visit(SingleStatement_print2 node) {
		Code.loadConst(node.getN2());
		Code.put(isChar(node.getExpr().struct) ? Code.bprint : Code.print);
	}

	@Override
	public void visit(SingleStatement_read node) {
		Obj obj = node.getDesignator().obj;
		Code.put(isChar(obj.getType()) ? Code.bread : Code.read);
		Code.store(obj);
	}

	@Override
	public void visit(SingleStatement_return1 node) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	@Override
	public void visit(SingleStatement_return2 node) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	/* USLOVI - kratko spajanje */

	private Stack<List<Integer>> condFactFalse = new Stack<>();
	private Stack<List<Integer>> condTermTrue = new Stack<>();
	private Stack<Integer> condFalseFix = new Stack<>();

	private int relop(Relop r) {
		if (r instanceof Relop_eq)  return Code.eq;
		if (r instanceof Relop_neq) return Code.ne;
		if (r instanceof Relop_lt)  return Code.lt;
		if (r instanceof Relop_lte) return Code.le;
		if (r instanceof Relop_gt)  return Code.gt;
		return Code.ge;
	}

	private void ensureStacks() {
		if (condFactFalse.isEmpty())
			condFactFalse.push(new ArrayList<Integer>());
		if (condTermTrue.isEmpty())
			condTermTrue.push(new ArrayList<Integer>());
	}

	@Override
	public void visit(CondFact_one node) {
		ensureStacks();
		Code.loadConst(0);
		Code.putFalseJump(Code.ne, 0);
		condFactFalse.peek().add(Code.pc - 2);
	}

	@Override
	public void visit(CondFact_relop node) {
		ensureStacks();
		Code.putFalseJump(relop(node.getRelop()), 0);
		condFactFalse.peek().add(Code.pc - 2);
	}

	@Override
	public void visit(CondTerm node) {
		ensureStacks();
		/* svi CondFact-i tacni -> ceo term tacan */
		Code.putJump(0);
		condTermTrue.peek().add(Code.pc - 2);
		/* netacni padaju ovde, na sledeci term */
		for (Integer addr : condFactFalse.peek())
			Code.fixup(addr);
		condFactFalse.peek().clear();
	}

	@Override
	public void visit(Condition node) {
		ensureStacks();
		/* svi termovi netacni -> uslov netacan */
		Code.putJump(0);
		condFalseFix.push(Code.pc - 2);
		/* tacni padaju ovde */
		for (Integer addr : condTermTrue.peek())
			Code.fixup(addr);
		condTermTrue.peek().clear();
	}

	/* IF - ELSE */

	private Stack<Integer> elseEnd = new Stack<>();

	@Override
	public void visit(ElseStmt_no node) {
		Code.fixup(condFalseFix.pop());
	}

	@Override
	public void visit(ElseMark node) {
		/* kraj then grane: preskoci else */
		Code.putJump(0);
		elseEnd.push(Code.pc - 2);
		Code.fixup(condFalseFix.pop());
	}

	@Override
	public void visit(ElseStmt_yes node) {
		Code.fixup(elseEnd.pop());
	}

	/* TERNARNI OPERATOR */

	private Stack<Integer> ternEnd = new Stack<>();

	@Override
	public void visit(TernaryMark node) {
		Code.putJump(0);
		ternEnd.push(Code.pc - 2);
		Code.fixup(condFalseFix.pop());
	}

	@Override
	public void visit(Expr_ternary node) {
		Code.fixup(ternEnd.pop());
	}

	/* PETLJE */

	private Stack<Integer> loopCondStart = new Stack<>();
//	private Stack<Integer> loopStepStart = new Stack<>();
	private Stack<Integer> loopBodyFix = new Stack<>();
	private Stack<Boolean> loopHasCond = new Stack<>();
	private Stack<List<Integer>> breakFix = new Stack<>();
	private Stack<Integer> continueFix = new Stack<>();

	@Override
	public void visit(ForCondMark node) {
		loopCondStart.push(Code.pc);
		breakFix.push(new ArrayList<Integer>());
	}
	
	@Override
	public void visit(WhileCondMark node) {
		loopCondStart.push(Code.pc);
		breakFix.push(new ArrayList<Integer>());
		continueFix.push(Code.pc);
	}

	@Override
	public void visit(ForCondition_c node) {
		loopHasCond.push(Boolean.TRUE);
	}

	@Override
	public void visit(ForCondition_e node) {
		loopHasCond.push(Boolean.FALSE);
	}

	@Override
	public void visit(ForStepMark node) {
		/* preskoci korak pri prvom prolazu */
		Code.putJump(0);
		loopBodyFix.push(Code.pc - 2);
//		loopStepStart.push(Code.pc);
		continueFix.push(Code.pc);
	}

	@Override
	public void visit(ForBodyMark node) {
		/* posle koraka nazad na uslov, pa telo */
		Code.putJump(loopCondStart.peek());
		Code.fixup(loopBodyFix.pop());
	}

	@Override
	public void visit(SingleStatement_for node) {
//		Code.putJump(loopStepStart.pop());
		Code.putJump(continueFix.pop());

		if (loopHasCond.pop())
			Code.fixup(condFalseFix.pop());

		for (Integer addr : breakFix.peek())
			Code.fixup(addr);
		breakFix.pop();
		loopCondStart.pop();
	}
	
	@Override
	public void visit(SingleStatement_while node) {
		Code.putJump(loopCondStart.pop());
		Code.fixup(condFalseFix.pop());
		
		for (Integer addr : breakFix.peek())
			Code.fixup(addr);		
		breakFix.pop();
		continueFix.pop();
	}

	@Override
	public void visit(SingleStatement_break node) {
		Code.putJump(0);
		breakFix.peek().add(Code.pc - 2);
	}

	@Override
	public void visit(SingleStatement_continue node) {
//		Code.putJump(loopStepStart.peek());
		Code.putJump(continueFix.peek());
	}

	/* FIND ANY */

	@Override
	public void visit(SingleStatement_findAny node) {
		Obj dest = node.getDesignator().obj;
		Obj src = node.getDesignator1().obj;
		Struct elemType = src.getType().getElemType();
		Obj elem = elemObj(elemType);

		/* na steku je vrednost Expr - sklanjamo je u pomocni slot */
		Code.put(Code.putstatic);
		Code.put2(tmpVal);

		Code.loadConst(0);                 // i

		int loopStart = Code.pc;
		Code.put(Code.dup);                // i, i
		Code.load(src);
		Code.put(Code.arraylength);        // i, i, len
		Code.putFalseJump(Code.lt, 0);     // i
		int notFound = Code.pc - 2;

		Code.put(Code.dup);                // i, i
		Code.load(src);                    // i, i, arr
		Code.put(Code.dup_x1);             // i, arr, i, arr
		Code.put(Code.pop);                // i, arr, i
		Code.load(elem);                   // i, elem
		Code.put(Code.getstatic);
		Code.put2(tmpVal);                 // i, elem, val
		Code.putFalseJump(Code.ne, 0);     // i   (skace ako su jednaki)
		int found = Code.pc - 2;

		Code.loadConst(1);
		Code.put(Code.add);
		Code.putJump(loopStart);

		Code.fixup(found);
		Code.put(Code.pop);
		Code.loadConst(1);
		Code.putJump(0);
		int end = Code.pc - 2;

		Code.fixup(notFound);
		Code.put(Code.pop);
		Code.loadConst(0);

		Code.fixup(end);
		Code.store(dest);
	}

	/* MAP */

	private Stack<Integer> mapLoopStart = new Stack<>();
	private Stack<Integer> mapEndFix = new Stack<>();

	@Override
	public void visit(MapMark node) {
		SingleStatement_map stmt = (SingleStatement_map) node.getParent();
		Obj dest = stmt.getDesignator().obj;
		Obj src = stmt.getDesignator1().obj;
		Struct elemType = src.getType().getElemType();
		Struct destElemType = dest.getType().getElemType();

		/* dest = new array[len(src)] */
		Code.load(src);
		Code.put(Code.arraylength);
		Code.put(Code.newarray);
		Code.put(isChar(destElemType) ? 0 : 1);
		Code.store(dest);

		/* i = 0 */
		Code.loadConst(0);
		Code.put(Code.putstatic);
		Code.put2(tmpIdx);

		int loopStart = Code.pc;
		mapLoopStart.push(loopStart);

		Code.put(Code.getstatic);
		Code.put2(tmpIdx);
		Code.load(src);
		Code.put(Code.arraylength);
		Code.putFalseJump(Code.lt, 0);
		mapEndFix.push(Code.pc - 2);

		/* ident = src[i] */
		Code.load(src);
		Code.put(Code.getstatic);
		Code.put2(tmpIdx);
		Code.load(elemObj(elemType));
		Code.store(mapIdents.get(stmt));
	}

	@Override
	public void visit(SingleStatement_map node) {
		Obj dest = node.getDesignator().obj;
		Struct destElem = dest.getType().getElemType();

		/* na steku je rezultat Expr */
		Code.put(Code.putstatic);
		Code.put2(tmpVal);

		Code.load(dest);
		Code.put(Code.getstatic);
		Code.put2(tmpIdx);
		Code.put(Code.getstatic);
		Code.put2(tmpVal);
		Code.store(elemObj(destElem));

		/* i++ */
		Code.put(Code.getstatic);
		Code.put2(tmpIdx);
		Code.loadConst(1);
		Code.put(Code.add);
		Code.put(Code.putstatic);
		Code.put2(tmpIdx);

		Code.putJump(mapLoopStart.pop());
		Code.fixup(mapEndFix.pop());
	}
	
	/* MAP FROM */

	private Stack<Integer> mapFromLoopStart = new Stack<>();
	private Stack<Integer> mapFromEndFix = new Stack<>();

	@Override
	public void visit(MapFromMark node) {
		SingleStatement_mapFrom stmt = (SingleStatement_mapFrom) node.getParent();
		Obj dest = stmt.getDesignator().obj;
		Obj src = stmt.getDesignator1().obj;
		Struct elemType = src.getType().getElemType();
		Struct destElemType = dest.getType().getElemType();
		
		/* i = poc, sa steka */
//		Code.loadConst(0);
		Code.put(Code.putstatic);
		Code.put2(tmpIdx);

		/* dest = new array[len(src)] */
		Code.load(src);
		Code.put(Code.arraylength);
		Code.put(Code.newarray);
		Code.put(isChar(destElemType) ? 0 : 1);
		Code.store(dest);

		int loopStart = Code.pc;
		mapFromLoopStart.push(loopStart);

		Code.put(Code.getstatic);
		Code.put2(tmpIdx);
		Code.load(src);
		Code.put(Code.arraylength);
		Code.putFalseJump(Code.lt, 0);
		mapFromEndFix.push(Code.pc - 2);

		/* ident = src[i] */
		Code.load(src);
		Code.put(Code.getstatic);
		Code.put2(tmpIdx);
		Code.load(elemObj(elemType));
		Code.store(mapFromIdents.get(stmt));
	}

	@Override
	public void visit(SingleStatement_mapFrom node) {
		Obj dest = node.getDesignator().obj;
		Struct destElem = dest.getType().getElemType();

		/* na steku je rezultat Expr */
		Code.put(Code.putstatic);
		Code.put2(tmpVal);

		Code.load(dest);
		Code.put(Code.getstatic);
		Code.put2(tmpIdx);
		Code.put(Code.getstatic);
		Code.put2(tmpVal);
		Code.store(elemObj(destElem));

		/* i++ */
		Code.put(Code.getstatic);
		Code.put2(tmpIdx);
		Code.loadConst(1);
		Code.put(Code.add);
		Code.put(Code.putstatic);
		Code.put2(tmpIdx);

		Code.putJump(mapFromLoopStart.pop());
		Code.fixup(mapFromEndFix.pop());
	}
}