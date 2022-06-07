package org.github.devlo.bom.properties.writter.maven.plugin;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mojo(name = "write", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class WriteMojo extends AbstractMojo {

    private enum FileType {
        POM, PROPERTY
    }

    /**
     * The repository system, used to resolve dependencies.
     */
    @Component
    private RepositorySystem repositorySystem;

    @Component
    private ProjectBuilder projectBuilder;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession mavenSession;

    @Parameter(property = "dependencies")
    private List<Dependency> dependencies = Collections.emptyList();

    @Parameter(property = "filterRegex", defaultValue = ".*")
    private String filterRegex;

    @Parameter(property = "fileDestination", required = true)
    private File fileDestination;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Pattern filterPattern = Pattern.compile(filterRegex);

        Map<String, String> newProperties = new LinkedHashMap<>();
        for (Dependency dependency : dependencies) {
            MavenProject dependencyMavenProject = getMavenProject(dependency);

            dependencyMavenProject.getProperties().forEach((key, value) -> {
                Matcher matcher = filterPattern.matcher((CharSequence) key);
                if (matcher.matches()) {
                    newProperties.put((String) key, (String) value);
                }
            });


        }

        writePomFile(newProperties);

    }

    private void writePomFile(Map<String, String> newProperties) throws MojoExecutionException {
        try {
            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model model = reader.read(new FileReader(fileDestination));

            for (Map.Entry<String, String> newProperty : newProperties.entrySet()) {
                if (!model.getProperties().contains(newProperty.getKey())) {
                    model.getProperties().put(newProperty.getKey(), newProperty.getValue());
                }
            }

            MavenXpp3Writer writer = new MavenXpp3Writer();
            writer.write(new FileWriter(fileDestination), model);
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException("File not found: " + e.getMessage());
        } catch (XmlPullParserException e) {
            throw new MojoExecutionException("Unable to read POM file: " + e.getMessage());
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private MavenProject getMavenProject(Dependency dependency) throws MojoExecutionException {
        try {
            Artifact artifact = repositorySystem.createDependencyArtifact(dependency);
            ProjectBuildingResult result = projectBuilder.build(artifact, mavenSession.getProjectBuildingRequest());
            return result.getProject();
        } catch (ProjectBuildingException e) {
            throw new MojoExecutionException("Could not obtain artifact for dependency " + dependency, e);
        }
    }

}
