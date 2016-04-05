package au.com.centrumsystems.hudson.plugin.buildpipeline;

import hudson.model.AbstractBuild;
import org.jgrapht.ext.VertexNameProvider;

import java.util.logging.Logger;

/**
 * @author tangkun75@gmail.com
 */
public class StringNameProvider implements VertexNameProvider<ExecutionBuildGraph.Vertex<AbstractBuild<?, ?>>> {
    /**
     * A Logger object is used to log messages
     */
    private static final Logger LOGGER = Logger.getLogger(StringNameProvider.class.getName());

    @Override
    public String getVertexName(ExecutionBuildGraph.Vertex<AbstractBuild<?, ?>> vertex) {
        LOGGER.fine(String.format("Build Name: %s", vertex.getBuild().toString()));
        return "\"" + vertex.getBuild().toString() + "\"";
    }
}
