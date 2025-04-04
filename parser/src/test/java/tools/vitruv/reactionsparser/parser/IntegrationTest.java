package tools.vitruv.reactionsparser.parser;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.function.BiConsumer;
import java.util.List;
import org.eclipse.emf.ecore.InternalEObject; 


import tools.vitruv.dsls.reactions.ReactionsLanguageStandaloneSetup;
import tools.vitruv.dsls.reactions.language.ModelAttributeReplacedChange;
import tools.vitruv.dsls.reactions.language.ModelElementChange;
import tools.vitruv.dsls.reactions.language.toplevelelements.ReactionsFile;

public class IntegrationTest {

    
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

    // === generateSemanticModel ===
    private void generateSemanticModel(ReactionsFile reactionsFile, String outputPath) {
        ResourceSet resourceSet = new ResourceSetImpl();
    
        // register .xmi 工厂
        Resource.Factory.Registry reg = Resource.Factory.Registry.INSTANCE;
        Map<String, Object> m = reg.getExtensionToFactoryMap();
        m.put("xmi", new XMIResourceFactoryImpl());
    
        // load semantic.ecore
        String projectRoot = new File(System.getProperty("user.dir")).getParent();
        String semanticEcorePath = projectRoot + "/model/src/main/ecore/semantic.ecore";
        Resource semanticEcore = resourceSet.getResource(URI.createFileURI(semanticEcorePath), true);
        EObject semanticRoot = semanticEcore.getContents().get(0);
    
        if (!(semanticRoot instanceof org.eclipse.emf.ecore.EPackage)) {
            fail("semantic.ecore root is not an EPackage");
        }
    
        var semanticPackage = (org.eclipse.emf.ecore.EPackage) semanticRoot;
        var factory = semanticPackage.getEFactoryInstance();
    
        var metamodelClass = (org.eclipse.emf.ecore.EClass) semanticPackage.getEClassifier("Metamodel");
        var metaclassClass = (org.eclipse.emf.ecore.EClass) semanticPackage.getEClassifier("Metaclass");
    
        // === map URI → metamodel name ===
        Map<String, String> uriToName = new java.util.HashMap<>();
        for (var mmImport : reactionsFile.getMetamodelImports()) {
            var pack = mmImport.getPackage();
            String uri = (pack != null && pack.eResource() != null) ? pack.eResource().getURI().toString() : null;
            if (uri != null) {
                uriToName.put(uri, mmImport.getName());
            }
        }
    
        // === map metamodel name → Set<metaclass name> ===
        Map<String, java.util.Set<String>> metamodelToMetaclasses = new java.util.HashMap<>();
        BiConsumer<String, String> addMetaclass = (uri, className) -> {
            String metamodelName = uriToName.get(uri);
            if (metamodelName == null) return;
            metamodelToMetaclasses.computeIfAbsent(metamodelName, k -> new java.util.HashSet<>()).add(className);
        };
    
        // === ReactionsFile metaclass href ===
        for (var segment : reactionsFile.getReactionsSegments()) {
            for (var reaction : segment.getReactions()) {
                var trigger = reaction.getTrigger();
                if (trigger instanceof ModelElementChange mec) {
                    var c = mec.getElementType().getMetaclass();
                    if (c instanceof EClass cls) {
                        extractHref(cls, addMetaclass);
                    }
                } else if (trigger instanceof ModelAttributeReplacedChange marc) {
                    var c = marc.getFeature().getMetaclass();
                    if (c instanceof EClass cls) {
                        extractHref(cls, addMetaclass);
                    }
                }
            }
    
            for (var routine : segment.getRoutines()) {
                for (var input : routine.getInput().getModelInputElements()) {
                    var c = input.getMetaclass();
                    if (c instanceof EClass cls) {
                        extractHref(cls, addMetaclass);
                    }
                }
    
                if (routine.getMatchBlock() != null) {
                    for (var match : routine.getMatchBlock().getMatchStatements()) {
                        if (match instanceof tools.vitruv.dsls.reactions.language.RetrieveModelElement retrieve) {
                            var elementType = retrieve.getElementType();
                            if (elementType != null && elementType.getMetaclass() instanceof EClass cls) {
                                extractHref(cls, addMetaclass);
                            }
                        }
                    }
                }

                if (routine.getCreateBlock() != null) {
                    for (var create : routine.getCreateBlock().getCreateStatements()) {
                        var c = create.getMetaclass();
                        if (c instanceof EClass cls) {
                            extractHref(cls, addMetaclass);
                        }
                    }
                }
            }
        }
    
        // ===  semantic.xmi model ===
        Resource semanticXmi = resourceSet.createResource(URI.createFileURI(outputPath));
    
        for (var mmImport : reactionsFile.getMetamodelImports()) {
            String metamodelName = mmImport.getName();
            var metamodelObj = factory.create(metamodelClass);
            metamodelObj.eSet(metamodelClass.getEStructuralFeature("name"), metamodelName);
    
            var metaclassSet = metamodelToMetaclasses.getOrDefault(metamodelName, Set.of());
            for (String metaclassName : metaclassSet) {
                var metaclassObj = factory.create(metaclassClass);
                metaclassObj.eSet(metaclassClass.getEStructuralFeature("name"), metaclassName);
                @SuppressWarnings("unchecked")
                List<EObject> list = (List<EObject>) metamodelObj.eGet(metamodelClass.getEStructuralFeature("metaclasses"));
                list.add(metaclassObj);
            }
    
            semanticXmi.getContents().add(metamodelObj);
        }
    
        try {
            semanticXmi.save(Map.of());
        } catch (IOException e) {
            e.printStackTrace();
            fail("Saving semantic.xmi failed: " + e.getMessage());
        }
    }
    
    private void extractHref(EClass eclass, BiConsumer<String, String> collector) {
        if (eclass == null) return;
    
        String uri = null;
        String className = null;
    
        if (eclass.eResource() != null) {
            uri = eclass.eResource().getURI().toString();
            className = eclass.getName();
        } else if (eclass.eIsProxy()) {
            var proxyUri = ((InternalEObject) eclass).eProxyURI();
            uri = proxyUri.trimFragment().toString();
            String fragment = proxyUri.fragment(); // e.g., //Task
            if (fragment != null && fragment.startsWith("//")) {
                className = fragment.substring(2);
            }
        }
    
        if (uri != null && className != null) {
            collector.accept(uri, className);
        }
    }
       
}
