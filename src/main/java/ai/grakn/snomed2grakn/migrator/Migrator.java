package ai.grakn.snomed2grakn.migrator;

import static ai.grakn.graql.Graql.insert;
import static ai.grakn.graql.Graql.var;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;

import ai.grakn.Grakn;
import ai.grakn.graql.VarName;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationSubject;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.util.SimpleShortFormProvider;

import ai.grakn.GraknGraph;
import ai.grakn.concept.EntityType;
import ai.grakn.concept.RelationType;
import ai.grakn.concept.ResourceType;
import ai.grakn.concept.RoleType;
import ai.grakn.graql.Graql;
import ai.grakn.graql.MatchQuery;
import ai.grakn.graql.Pattern;
import ai.grakn.graql.QueryBuilder;
import ai.grakn.graql.Var;


/**
 * 
 * <p>
 * The Migrator is the main driver of the SNOMED_CT migration process.
 * </p>
 *
 * @author Szymon Klarman
 *
 */

public class Migrator {
	
	public static HashMap<OWLObjectProperty, String[]> relationTypes = new HashMap<OWLObjectProperty, String[]>();
	public static HashMap<OWLClass, String> entities = new HashMap<OWLClass, String>();
	public static int counter=0;
	public static OWLDataFactory df = OWLManager.createOWLOntologyManager().getOWLDataFactory();
	public static OWLAnnotationProperty labProp = df.getRDFSLabel();

static void migrateSNOMED (OWLOntology snomed, GraknGraph graknGraph) {
		
	
		Instant start = Instant.now();
	
		//registering some top-level predicates
		EntityType owlClass = graknGraph.putEntityType("owl-class");
		EntityType snomedClass = graknGraph.putEntityType("named-class");
		EntityType intersectionClass = graknGraph.putEntityType("intersection-class");
		EntityType existentialClass = graknGraph.putEntityType("existential-class");
		snomedClass.superType(owlClass);
		intersectionClass.superType(owlClass);
		existentialClass.superType(owlClass);
		ResourceType snomedUriRes = graknGraph.putResourceType("snomed-uri", ResourceType.DataType.STRING);
		ResourceType<String> snomedLabelRes = graknGraph.putResourceType("label", ResourceType.DataType.STRING);
		owlClass.resource(snomedUriRes);
		owlClass.resource(snomedLabelRes);
		RelationType subclassing = graknGraph.putRelationType("subclassing");
		RoleType subclass = graknGraph.putRoleType("subclass");
		RoleType superclass = graknGraph.putRoleType("superclass");
		subclassing.hasRole(subclass);
		subclassing.hasRole(superclass);
		owlClass.playsRole(subclass);
		owlClass.playsRole(superclass);
		RelationType owlTopProperty = graknGraph.putRelationType("owl-property").setAbstract(true);
		//graknGraph.putRuleType("property-chain").superType(graknGraph.admin().getMetaRuleInference());
		//graknGraph.putRuleType("inverse-property").superType(graknGraph.admin().getMetaRuleInference());
		//graknGraph.putRuleType("inheritance").superType(graknGraph.admin().getMetaRuleInference());
			
		Pattern atom1 = var().isa("subclassing").rel("subclass", "x").rel("superclass", "y");
		Pattern atom2 = var().isa(var("rel")).rel(var("rel-role-1"), "y").rel(var("rel-role-2"), "z");
		Pattern atom3 = var("rel").sub("owl-property");
		Pattern body1 = Graql.and(atom1, atom2, atom3);
		Pattern head1 = var().isa(var("rel")).rel(var("rel-role-1"), "x").rel(var("rel-role-2"), "z");
		graknGraph.getRuleType("inference-rule").putRule(body1, head1);
		Pattern atom4 = var().isa("subclassing").rel("subclass", "y").rel("superclass", "z");
		Pattern body2 = Graql.and(atom1, atom4);
		Pattern head2 = var().isa("subclassing").rel("subclass", "x").rel("superclass", "z");
		graknGraph.getRuleType("inference-rule").putRule(body2, head2);
		Pattern head3 = var().isa("subclassing").rel("subclass", "x").rel("superclass", "x");
		Pattern body3 = var("x").isa("named-class");
		graknGraph.getRuleType("inference-rule").putRule(body3, head3);

		Main.graphCommitReopen();

	//registering named OWL properties in SNOMED as relations
		System.out.println("\nRegistering properties...");
	
		snomed.objectPropertiesInSignature().forEach(snomedProperty -> {
			count();
			String relationName = getLabel(snomedProperty, snomed);
			String fromRoleName = relationName + "-from";
			String toRoleName = relationName + "-to";
			//System.out.println(relationName);
			RelationType relation = graknGraph.putRelationType(relationName);
			RoleType from = graknGraph.putRoleType(fromRoleName);
			RoleType to = graknGraph.putRoleType(toRoleName);
			relation.hasRole(from);
			relation.hasRole(to);
			owlClass.playsRole(from);
			owlClass.playsRole(to);
			relation.superType(owlTopProperty);
			String[] relationInfo = {relationName, fromRoleName, toRoleName}; 
			relationTypes.put(snomedProperty, relationInfo);
		});
		
		System.out.println("\nProperties registered: " + counter);

        Pattern atom5 = var().isa("Role-group-(attribute)").rel("Role-group-(attribute)-from", "x").rel("Role-group-(attribute)-to", "y");
        Pattern body4 = Graql.and(atom5, atom2, atom3);
        graknGraph.getRuleType("inference-rule").putRule(body4, head1);

	    Main.graphCommitReopen();

		//registering named OWL classes in SNOMED as entities 
		System.out.println("\nRegistering classes...");
		counter=0;
		snomed.classesInSignature().forEach(snomedNamedClass -> {
			count();
			String snomedUri = "snomed:" + shortName(snomedNamedClass);
			String snomedLabel = getLabel(snomedNamedClass, snomed);
			Var entityPattern = var().isa("named-class").has("snomed-uri", snomedUri).has("label", snomedLabel);
			Main.loaderClient.add(insert(entityPattern));
			entities.put(snomedNamedClass, snomedUri);
		});

		Main.loaderClient.flush();
    	Main.loaderClient.waitToFinish();
		Main.graphCommitReopen();

		QueryBuilder qb = graknGraph.graql();
		snomed.classesInSignature().forEach(snomedNamedClass -> {
			String snomedUri = entities.get(snomedNamedClass);
			MatchQuery idRetrieve = qb.match(var("x").has("snomed-uri", snomedUri));
			entities.put(snomedNamedClass, idRetrieve.iterator().next().get("x").getId().toString());
		});
		System.out.println("\nClasses registered: " + counter);

		//Extracting and structuring information from OWL axioms in SNOMED
		System.out.println("\nMigrating axioms...");
		counter=0;
		
		OWL2GraknAxiomVisitor visitor = new OWL2GraknAxiomVisitor();
		snomed.axioms().forEach(ax ->  {
			ax.accept(visitor);
			});
        Main.loaderClient.flush();
		Main.loaderClient.waitToFinish();
		System.out.println("\nAxioms migrated: " + counter);
		Instant end = Instant.now();
		System.out.println("\nMigration finished in: " + Duration.between(start, end)); 
	}
    
public static String shortName(OWLEntity id) {
    SimpleShortFormProvider shortform = new SimpleShortFormProvider();
    return shortform.getShortForm(id); 
}

public static String getLabel(OWLEntity id, OWLOntology ontology) {
	OWLAnnotationAssertionAxiom annAx = ontology.annotationAssertionAxioms((OWLAnnotationSubject) id.getIRI()).filter(ann -> ann.getAnnotation().getProperty().equals(labProp)).findFirst().orElse(null);
	if (annAx!=null) return annAx.getAnnotation().getValue().toString().split("\"")[1].replace(" ", "-"); 
	else return shortName(id); 
}

public static void count() {
	counter++;
	if (counter % 1000 == 0) {
		System.out.print(counter/1000 + "K..");
	}

	if (counter % 10000 == 0) {
		Main.graphCommitReopen();
	}
}
}
