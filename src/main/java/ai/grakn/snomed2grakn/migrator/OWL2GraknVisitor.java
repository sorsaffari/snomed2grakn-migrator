package ai.grakn.snomed2grakn.migrator;

import static ai.grakn.graql.Graql.insert;
import static ai.grakn.graql.Graql.var;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ai.grakn.concept.ConceptId;
import org.semanticweb.owlapi.model.OWLAxiomVisitor;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLClassExpressionVisitorEx;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLInverseObjectPropertiesAxiom;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.OWLSubObjectPropertyOfAxiom;
import org.semanticweb.owlapi.model.OWLSubPropertyChainOfAxiom;

import ai.grakn.graql.Graql;
import ai.grakn.graql.Pattern;
import ai.grakn.graql.Var;


/**
 * <p>
 * This is the class processing OWL axioms and complex expressions. Note that the named OWL classes and object 
 * properties are first registered by the Migrator.  
 * </p>
 * 
 * @author Szymon Klarman
 *
 */


class OWL2GraknAxiomVisitor implements OWLAxiomVisitor {
	
	
    public void visit(OWLSubClassOfAxiom ax) {
    	Migrator.count();
    	OWLClassExpression subClass = ax.getSubClass();
    	OWLClassExpression superClass = ax.getSuperClass();
    	
    	OWL2GraknExpressionVisitor visitor = new OWL2GraknExpressionVisitor();
    	
    	Snomed2GraknPattern subPattern = subClass.accept(visitor);
    	Snomed2GraknPattern superPattern = superClass.accept(visitor);
		   	
    	Var relationPattern = var().rel("subclass", var(subPattern.var)).rel("superclass", var(superPattern.var)).isa("subclassing");
    	
    	List<Var> insertPatternList = Snomed2GraknPattern.varConcat(Snomed2GraknPattern.varConcat(subPattern.insertPatternList, superPattern.insertPatternList), relationPattern);
    	Var[] insertPattern = insertPatternList.toArray(new Var[insertPatternList.size()]);
    	
    	Main.loaderClient.add(insert(insertPattern));
   }
    
    public void visit(OWLEquivalentClassesAxiom ax) {
    	Migrator.count();
    	List<OWLClassExpression> classes = ax.classExpressions().collect(Collectors.toList());
     	
    	OWL2GraknExpressionVisitor visitor = new OWL2GraknExpressionVisitor();
    	
    	Snomed2GraknPattern entity1 = classes.get(0).accept(visitor);
    	Snomed2GraknPattern entity2 = classes.get(1).accept(visitor);
    	   	
    	Var relationPattern1 = var().rel("subclass", var(entity1.var)).rel("superclass", var(entity2.var)).isa("subclassing");
    	Var relationPattern2 = var().rel("subclass", var(entity2.var)).rel("superclass", var(entity1.var)).isa("subclassing");
    	
     	List<Var> insertPatternList = Snomed2GraknPattern.varConcat(Snomed2GraknPattern.varConcat(Snomed2GraknPattern.varConcat(entity1.insertPatternList, entity2.insertPatternList), relationPattern1), relationPattern2);
      	Var[] insertPattern = insertPatternList.toArray(new Var[insertPatternList.size()]);
    	
      	Main.loaderClient.add(insert(insertPattern));
   }
    
    public void visit(OWLSubObjectPropertyOfAxiom ax) {
    	Migrator.count();
		OWLObjectProperty subProperty = (OWLObjectProperty) ax.getSubProperty();
		String[] subRelationInfo = Migrator.relationTypes.get(subProperty);
		OWLObjectProperty superProperty = (OWLObjectProperty) ax.getSuperProperty();
		String[] superRelationInfo = Migrator.relationTypes.get(superProperty);
		Main.graknGraph.getRelationType(subRelationInfo[0]).superType(Main.graknGraph.getRelationType(superRelationInfo[0]));
		Main.graknGraph.getRoleType(subRelationInfo[1]).superType(Main.graknGraph.getRoleType(superRelationInfo[1]));
		Main.graknGraph.getRoleType(subRelationInfo[2]).superType(Main.graknGraph.getRoleType(superRelationInfo[2]));
	}
	
	public void visit(OWLSubPropertyChainOfAxiom ax) {
		Migrator.count();
		List<OWLObjectPropertyExpression> subProperties = ax.getPropertyChain();
		if (subProperties.size()!=2) return;
		String[] superRelationInfo = Migrator.relationTypes.get(ax.getSuperProperty().asOWLObjectProperty());
		
		String[] leftSubRelationInfo = Migrator.relationTypes.get(subProperties.get(0).asOWLObjectProperty());
		String[] rightSubRelationInfo = Migrator.relationTypes.get(subProperties.get(1).asOWLObjectProperty());
		
		Pattern leftSub = var().isa(leftSubRelationInfo[0]).rel(leftSubRelationInfo[1], "x").rel(leftSubRelationInfo[2], "y");
	    Pattern rightSub = var().isa(rightSubRelationInfo[0]).rel(rightSubRelationInfo[1], "y").rel(rightSubRelationInfo[2], "z");
	    Pattern body = Graql.and(leftSub, rightSub);
	    Pattern head = var().isa(superRelationInfo[0]).rel(superRelationInfo[1], "x").rel(superRelationInfo[2], "z");
	    Main.graknGraph.getRuleType("inference-rule").putRule(body, head);
	}	
	
	public void visit(OWLInverseObjectPropertiesAxiom ax) {
		Migrator.count();
		String[] firstRelationInfo = Migrator.relationTypes.get(ax.getFirstProperty());
		String[] secondRelationInfo = Migrator.relationTypes.get(ax.getSecondProperty());
		
		Pattern body = var().isa(secondRelationInfo[0]).rel(secondRelationInfo[1], "x").rel(secondRelationInfo[2], "y");
		Pattern head = var().isa(firstRelationInfo[0]).rel(firstRelationInfo[1], "y").rel(firstRelationInfo[2], "x");
		Main.graknGraph.getRuleType("inference-rule").putRule(body, head);
		Main.graknGraph.getRuleType("inference-rule").putRule(head, body);
	}	
}

class OWL2GraknExpressionVisitor implements OWLClassExpressionVisitorEx<Snomed2GraknPattern> {
  
	
	public static int varNo = 0;
	
	public static String getNewVarName() {
		varNo++;
		return "var"+varNo;
	}
	
	public Snomed2GraknPattern visit(OWLClass exp) {
		String classVar = getNewVarName();
		ConceptId graknId = ConceptId.of(Migrator.entities.get(exp));
		Var insertPattern = var(classVar).id(graknId);
		return new Snomed2GraknPattern(insertPattern, classVar);
	}
	
	public Snomed2GraknPattern visit(OWLObjectIntersectionOf exp) {
		String conjunctionVar = getNewVarName();
		Var conjunctionEntityPattern = var(conjunctionVar).isa("intersection-class");
		
		List<Var> insertPatternList = Arrays.asList(conjunctionEntityPattern);

		for (OWLClassExpression conjunctEntity : exp.asConjunctSet()) {
			Snomed2GraknPattern conjunctEntityPattern = conjunctEntity.accept(this);
			insertPatternList = Snomed2GraknPattern.varConcat(insertPatternList, conjunctEntityPattern.insertPatternList);
			Var relationPattern = var().rel("subclass", var(conjunctionVar)).rel("superclass", var(conjunctEntityPattern.var)).isa("subclassing");
			insertPatternList.add(relationPattern);
		}
		
		return new Snomed2GraknPattern(insertPatternList, conjunctionVar);
	}
	
	public Snomed2GraknPattern visit(OWLObjectSomeValuesFrom exp) {
		String existentialVar = getNewVarName();
		
		OWLObjectProperty property = (OWLObjectProperty) exp.getProperty();
		String[] relationInfo = Migrator.relationTypes.get(property);
				
		OWLClassExpression fillerExpression = exp.getFiller();
		Snomed2GraknPattern fillerEntityPattern = fillerExpression.accept(this);
		
		List<Var> insertPatternList = fillerEntityPattern.insertPatternList;
		
		Var existentialEntityPattern = var(existentialVar).isa("existential-class");
		Var relationPattern = var().rel(relationInfo[1], var(existentialVar)).rel(relationInfo[2], var(fillerEntityPattern.var)).isa(relationInfo[0]);
		
		insertPatternList = Snomed2GraknPattern.varConcat(insertPatternList, existentialEntityPattern);
		insertPatternList = Snomed2GraknPattern.varConcat(insertPatternList, relationPattern);

		return new Snomed2GraknPattern(insertPatternList, existentialVar);
	}
	
}

class Snomed2GraknPattern{
	List<Var> insertPatternList;
	String var;
	
	Snomed2GraknPattern(List<Var> insertList, String var) {
		this.insertPatternList = insertList;
		this.var = var;
	}
	
	Snomed2GraknPattern(Var insert, String var) {
		this.insertPatternList = Arrays.asList(insert);
		this.var = var;
	}
	
	public static List<Var> varConcat(List<Var> firstList, List<Var> secondList) {
		return Stream.concat(firstList.stream(), secondList.stream()).collect(Collectors.toList());
	}
	
	public static List<Var> varConcat(List<Var> patternList, Var pattern) {
		return Stream.concat(patternList.stream(), Arrays.asList(pattern).stream()).collect(Collectors.toList());
	}
}
