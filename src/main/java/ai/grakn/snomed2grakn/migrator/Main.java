package ai.grakn.snomed2grakn.migrator;

import java.io.File;

import ai.grakn.GraknTxType;
import ai.grakn.client.LoaderClient;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import ai.grakn.Grakn;
import ai.grakn.GraknGraph;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * <p>
 * Main program to migrate SNOMED-CT OWL ontology into a Grakn knowledge graph. 
 * Note that currently the input file is fixed to snomedSample.owl which should be
 * included in the project directory. 
 * </p>
 * 
 * @author Szymon Klarman
 *
 */


public class Main 
{	
	static String keyspace = "grakn";
	public static GraknGraph graknGraph;
	public static LoaderClient loaderClient;
	
    public static void main( String[] args )
    {
    	Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME); 
    	logger.setLevel(Level.OFF);
    	
    	if (args.length==0) {
    		System.out.println("You must provide the name of the OWL file containing SNOMED-CT.");
    		System.exit(0);
    	}
    	
    	File input = new File(args[0]);
		
		if (!input.exists()) {
			System.out.println("Could not find the file: " + input.getAbsoluteFile());
			System.exit(0);
		}

		graknGraph = Grakn.session(Grakn.DEFAULT_URI, keyspace).open(GraknTxType.WRITE);
    	loaderClient = new LoaderClient(keyspace, Grakn.DEFAULT_URI);
		//graknGraph.commitOnClose();

		try{
			System.out.println("Loading SNOMED...");
			OWLOntology ontology = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(input);
			Migrator.migrateSNOMED(ontology, graknGraph);

			graknGraph.commit();
			System.exit(0);
		}
		catch (OWLOntologyCreationException e) {
			System.out.println("Could not load ontology: " + e.getMessage());
		} 
	}

	public static void graphCommitReopen() {
		graknGraph.commit();
		graknGraph = Grakn.session(Grakn.DEFAULT_URI, keyspace).open(GraknTxType.WRITE);
	}
}
