package org.eurocris.cerif.tools;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.ws.commons.schema.XmlSchema;
import org.apache.ws.commons.schema.XmlSchemaCollection;
import org.apache.ws.commons.schema.XmlSchemaElement;
import org.apache.ws.commons.schema.XmlSchemaObject;
import org.apache.ws.commons.schema.extensions.ExtensionDeserializer;
import org.apache.ws.commons.schema.extensions.ExtensionRegistry;
import org.eurocris.cerif.model.CERIFEntityType;
import org.eurocris.cerif.model.Entity;
import org.eurocris.cerif.model.Model;
import org.eurocris.cerif.model.toad.ToadModelParser;
import org.w3c.dom.Attr;
import org.w3c.dom.Node;

public class ToadXsdTools {

	public static final String CFLINK_NS_URI = "http://eurocris.org/cerif/annotations#";
	
	public static void main( final String[] args ) {
		try {

			final CommandLineParser parser = new DefaultParser();
	    	final Options options = new Options();
	    	options.addOption("m", "model", true, "full path to the TOAD file");
	    	options.addOption("s", "schema", true, "full path to the XSD file");
	    	options.addOption("h", "help", true, "this help message");
	    	
	    	final CommandLine line = parser.parse(options, args);
	    	
	    	if (line.hasOption("h") || !(line.hasOption("m") && line.hasOption("s")))
	    	{
	    		HelpFormatter formatter = new HelpFormatter();
	    		formatter.printHelp( "toad2cerif", options );
	    		System.exit(line.hasOption("h")?0:1);
	    	}
	    	
	    	final File modelFile = new File(line.getOptionValue('m'));
			final ToadModelParser modelParser = new ToadModelParser();
			final Model model = modelParser.readInModel( modelFile );
			
			final Map<String, Entity> basicEntitiesByName = new LinkedHashMap<>();
			for ( final Entity e : model.iterableEntities() ) {
				final CERIFEntityType et = e.getEntityType();
				if ( et != null ) {
					switch ( et ) {
					case BASE_ENTITIES:
					case ADDITIONAL_ENTITIES:
					case INDICATORS_AND_MEASUREMENTS:
					case INFRASTRUCTURE_ENTITIES:
					case RESULT_ENTITIES:
					case SECOND_ORDER_ENTITIES:
						basicEntitiesByName.put( e.getPhysicalName(), e );
						break;
					default:
						break;
					}
				} else {
					System.err.println( "Entity '" + e.getPhysicalName() + "' doesn't have a type" );
				}
			}

			final File schemaFile = new File(line.getOptionValue('s'));
			final XmlSchemaCollection schemaCol = new XmlSchemaCollection();
			schemaCol.setExtReg( new MyExtensionRegistry( model ) );
			final XmlSchema schema = schemaCol.read(new StreamSource(schemaFile));
			final Map<QName, XmlSchemaElement> elements = schema.getElements();
			System.out.println( "Element names: " + elements.keySet() );
			for ( final XmlSchemaElement elDecl : elements.values() ) {
				final Map<Object, Object> metaInfoMap = elDecl.getMetaInfoMap();
				final Entity e = ( metaInfoMap != null ) ? (Entity) metaInfoMap.get( EntityLinkExtensionDeserializer.CFLINK_ENTITY_QNAME ) : null;
				if ( e != null ) {
					final String entityName = e.getPhysicalName();
					if ( basicEntitiesByName.containsKey( entityName ) ) {
						basicEntitiesByName.remove( entityName );
						// ok
					} else {
						System.out.println( "Entity '" + entityName + "' is not known" );
					}					
				}
			}
			
			for ( final String e : basicEntitiesByName.keySet() ) {
				System.out.println( "Entity '" + e + "' is not represented in the schema" );
			}
			

		} catch ( final Throwable t ) {
			t.printStackTrace( System.err );
		}
	}

	protected static class MyExtensionRegistry extends ExtensionRegistry {
		protected MyExtensionRegistry( final Model model ) {
			super();
			registerDeserializer( EntityLinkExtensionDeserializer.CFLINK_ENTITY_QNAME, new EntityLinkExtensionDeserializer( model ) );
		}
	}
	
	protected static class EntityLinkExtensionDeserializer implements ExtensionDeserializer {

		public static final QName CFLINK_ENTITY_QNAME = new QName( CFLINK_NS_URI, "entity" );

		private final Model model;
		
		public EntityLinkExtensionDeserializer( final Model model ) {
			this.model = model;
		}

		@Override
		public void deserialize( final XmlSchemaObject obj, final QName qname, final Node node ) {
			final Attr attr = (Attr) node;
			final String name = attr.getValue();
			final Entity entity = model.getEntityBy( name );
			if ( entity != null ) {
				obj.addMetaInfo( CFLINK_ENTITY_QNAME, entity );
			} else {
				System.err.println( "Entity '" + name + "' not found in the model" );
			}
		}
		
	}

}
