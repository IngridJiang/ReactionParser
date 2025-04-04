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

    private static final String SEMANTIC_NS_URI = "http://www.example.org/semantic";
    
    @BeforeAll
    public static void setupAll() {
        ReactionsLanguageStandaloneSetup.doSetup();
    }

    @Test
    public void testMultipleReactionsFiles() {
        testReactionsParsing("template.reactions", "template.xmi");    
        testReactionsParsing("template2.reactions", "template2.xmi");   
    }

  
    private void testReactionsParsing(String inputFile, String outputFile) {
        var parser = new GenericXtextParser();
        ReactionsFile result = null;

        try {
            String inputPath = resourcePath(inputFile);  
            result = (ReactionsFile) parser.parse(inputPath);
        } catch (Exception e) {
            fail("Parsing failed for " + inputFile + ": " + e.getMessage());
        }

        
        for (var mmImport : result.getMetamodelImports()) {
            System.out.println("[" + inputFile + "] import " + mmImport.getName());
        }

        try {
            save(result, outputFile);  
            generateSemanticModel(result, outputFile.replace(".xmi", "_semantic.xmi"));
        } catch (IOException e) {
            e.printStackTrace();
            fail("Saving XMI failed for " + outputFile);
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

    private void generateSemanticModel(ReactionsFile reactionsFile, String outputPath) {
        ResourceSet resourceSet = new ResourceSetImpl();
        
        // register .xmi factory
        Resource.Factory.Registry reg = Resource.Factory.Registry.INSTANCE;
        Map<String, Object> m = reg.getExtensionToFactoryMap();
        m.put("xmi", new XMIResourceFactoryImpl());
    
        // load semantic.ecore
        String projectRoot = new File(System.getProperty("user.dir")).getParent(); // 父目录是 model 和 parser 的公共祖先
        String semanticEcorePath = projectRoot + "/model/src/main/ecore/semantic.ecore";

        Resource semanticEcore = resourceSet.getResource(URI.createFileURI(semanticEcorePath), true);
        EObject semanticRoot = semanticEcore.getContents().get(0);
        
        if (!(semanticRoot instanceof org.eclipse.emf.ecore.EPackage)) {
            fail("semantic.ecore root is not an EPackage");
        }
        
        var semanticPackage = (org.eclipse.emf.ecore.EPackage) semanticRoot;
        var factory = semanticPackage.getEFactoryInstance();
    
        // create semantic.xmi resource
        Resource semanticXmi = resourceSet.createResource(URI.createFileURI(outputPath));
    
        // 获取 metamodel import 的名称
        var imports = reactionsFile.getMetamodelImports();
        if (imports.size() < 2) {
            fail("Less than 2 metamodel imports found.");
        }
    
        // 创建两个 Metamodel 实例
        var metamodelClass = (org.eclipse.emf.ecore.EClass) semanticPackage.getEClassifier("Metamodel");
    
        for (int i = 0; i < 2; i++) {
            var mm = factory.create(metamodelClass);
            mm.eSet(metamodelClass.getEStructuralFeature("name"), imports.get(i).getName());
            semanticXmi.getContents().add(mm);
        }
    
        // save semantic.xmi
        try {
            semanticXmi.save(Map.of());
        } catch (IOException e) {
            e.printStackTrace();
            fail("Saving semantic.xmi failed: " + e.getMessage());
        }
    }
}
