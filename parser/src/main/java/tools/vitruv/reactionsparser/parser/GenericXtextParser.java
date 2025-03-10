package tools.vitruv.reactionsparser.parser;

import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.xtext.resource.IResourceServiceProvider;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.Issue;

public class GenericXtextParser {

    public EObject parse(String path) throws Exception {
        // load
        var resourceServiceProvider = IResourceServiceProvider.Registry.INSTANCE.getResourceServiceProvider(URI.createFileURI(path));
        var resourceSet = resourceServiceProvider.get(ResourceSet.class);
        var resource = resourceSet.getResource(URI.createFileURI(path), true);

        // validate
        var validator = ((XtextResource) resource).getResourceServiceProvider().getResourceValidator();
        var issues = validator.validate(resource, CheckMode.ALL, CancelIndicator.NullImpl);
        if (!issues.isEmpty()) {
            throw new Exception(generateIssuesMessage(issues));
        }

        // return
        return (EObject) resource.getContents().get(0);
    }

    String generateIssuesMessage(List<Issue> issues) {
        return String.join(System.lineSeparator(), issues.stream().map(issue -> issue.getMessage()).toList());
    }

}