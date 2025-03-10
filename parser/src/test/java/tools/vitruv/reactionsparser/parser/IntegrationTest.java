package tools.vitruv.reactionsparser.parser;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import tools.vitruv.dsls.reactions.ReactionsLanguageStandaloneSetup;
import tools.vitruv.dsls.reactions.language.toplevelelements.ReactionsFile;

public class IntegrationTest {

    @BeforeAll
    public static void setupAll() {
        ReactionsLanguageStandaloneSetup.doSetup();
    }

    private String inputPath = resourcePath("template.reactions");
    private String outputPath = "template.xmi";

    @Test
    public void testParse() {
        // parse Reactions
        var parser = new GenericXtextParser();
        ReactionsFile result = null;
        try {
            result = (ReactionsFile) parser.parse(inputPath);
        } catch (Exception e) {
            fail(e);
        }

        // access Reactions (just as an example)
        for (var mmImport : result.getMetamodelImports()) {
            System.out.println("import " + mmImport.getName());
        }

        // save Reactions file as xmi
        try {
            save(result, outputPath);
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
    }

    private String resourcePath(String fileName) {
        return new File(this.getClass().getClassLoader().getResource(fileName).getFile()).getAbsolutePath();
    }

    private void save(EObject content, String path) throws IOException {
        Resource.Factory.Registry reg = Resource.Factory.Registry.INSTANCE;
        Map<String, Object> m = reg.getExtensionToFactoryMap();
        m.put("xmi", new XMIResourceFactoryImpl());

        ResourceSet resSet = new ResourceSetImpl();

        Resource resource = resSet.createResource(URI.createURI(path));
        resource.getContents().add(content);

        resource.save(Map.of());
    }

}