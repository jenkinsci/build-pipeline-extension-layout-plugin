package au.com.centrumsystems.hudson.plugin.buildpipeline;

import au.com.centrumsystems.hudson.plugin.util.BuildUtil;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.plugins.parameterizedtrigger.BlockableBuildTriggerConfig;
import hudson.plugins.parameterizedtrigger.SubProjectsAction;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.buildgraphview.DownStreamRunDeclarer;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Get sub projects' builds
 *
 * @author tangkun75@gmail.com
 */
@Extension
public class SubProjectsDownStreamRunDeclarer extends DownStreamRunDeclarer {
    /**
     * A Logger object is used to log messages
     */
    private static final Logger LOGGER = Logger.getLogger(SubProjectsDownStreamRunDeclarer.class.getName());

    @Override
    public List<Run> getDownStream(Run r) {
        final List<Run> runs = new ArrayList<Run>();
        final AbstractBuild<?, ?> currentBuild = (AbstractBuild<?, ?>) r;
        if (r != null) {
            final AbstractProject<?, ?> currentProject = ((AbstractBuild<?, ?>) r).getProject();
            final Jenkins jenkins = Jenkins.getInstance();
            if (jenkins != null) {
                if (jenkins.getPlugin("parameterized-trigger") != null) {
                    for (SubProjectsAction action : Util.filter(currentProject.getActions(), SubProjectsAction.class)) {
                        for (BlockableBuildTriggerConfig config : action.getConfigs()) {
                            for (final AbstractProject<?, ?> dependency : config.getProjectList(currentProject.getParent(), null)) {
                                AbstractBuild<?, ?> returnedBuild = null;
                                returnedBuild = BuildUtil.getDownstreamBuild(dependency, currentBuild);
                                LOGGER.fine(String.format("Find %s downstream build: %s", currentBuild.toString(),
                                        returnedBuild.toString()));
                                runs.add(returnedBuild);
                            }
                        }
                    }
                }
            }
        }
        return runs;
    }
}
