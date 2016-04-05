package au.com.centrumsystems.hudson.plugin.buildpipeline;

import com.cloudbees.plugins.flow.FlowDownStreamRunDeclarer;
import hudson.Functions;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.util.LogTaskListener;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.buildgraphview.DownStreamRunDeclarer;
import org.jenkinsci.plugins.buildgraphview.UpstreamCauseDonwStreamRunDeclarer;
import org.jgrapht.DirectedGraph;
import org.jgrapht.ext.DOTExporter;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.unix4j.Unix4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Get the graph of the build and its dependents and do some transform for showing on build-pipleline
 *
 * @author tangkun75@gmail.com
 */
public class ExecutionBuildGraph {
    /**
     * A Logger object is used to log messages
     */
    private static final Logger LOGGER = Logger.getLogger(ExecutionBuildGraph.class.getName());

    /**
     * build execution graph which build is vertex, downstream/upstream relationship is edge
     */
    private DirectedGraph<Vertex<AbstractBuild<?, ?>>, Edge> graph;

    /**
     * builds' layout info for build pipeline view
     */
    private Map<Vertex<AbstractBuild<?, ?>>, Position> graphLayout;

    /**
     * the start point (build) or root of the graph
     */
    private Vertex<AbstractBuild<?, ?>> start;

    /**
     * @param vertex a start project build for calculating build graph)
     */
    public ExecutionBuildGraph(Vertex<AbstractBuild<?, ?>> vertex) {
        this.start = vertex;
        this.graphLayout = new HashMap<Vertex<AbstractBuild<?, ?>>, Position>();
    }

    /**
     * Calculate the build graph of the start project build via traversing all of instances of DownStreamRunDeclarer
     *
     * @return the whole graph of the start project build with transform information for showing on the build-pipeline
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public DirectedGraph<Vertex<AbstractBuild<?, ?>>, Edge> getGraph() throws ExecutionException,
            InterruptedException {
        graph = new SimpleDirectedGraph<Vertex<AbstractBuild<?, ?>>, Edge>(Edge.class);
        graph.addVertex(start);
        computeGraphFrom(start);
        layoutForPipelineView();
        return this.graph;
    }

    /**
     * @return the position information after transform again based on graphviz plain transform output
     */
    public Map<Vertex<AbstractBuild<?, ?>>, Position> getGraphLayout() {
        return this.graphLayout;
    }

    /**
     * Layout for build execution graph
     */
    private void layoutForPipelineView() {
        graphvizLayout(this.graph);
        transform(this.graph, this.start, 0, 0, this.graphLayout);
    }

    /**
     * Transform the graphviz layout to the style for build pipeline view (Build Grid)
     *
     * @param graph       graph
     * @param startBuild  startBuild
     * @param startX      column number
     * @param startY      row number
     * @param traveredMap layout info
     * @return the column number after transforming
     */
    private int transform(DirectedGraph<Vertex<AbstractBuild<?, ?>>, Edge> graph, Vertex<AbstractBuild<?,
            ?>> startBuild, int startX, int
                                  startY, Map<Vertex<AbstractBuild<?, ?>>, Position> traveredMap) {
        int x, y;
        x = startX;
        y = startY;
        if (!traveredMap.containsKey(startBuild)) {
            traveredMap.put(startBuild, new Position(x, y));
        } else {
            if (traveredMap.get(startBuild).y < y) {
                traveredMap.get(startBuild).y = y;
            } else {
                return x + 1;
            }
        }
        if (graph.outDegreeOf(startBuild) > 0) {
            y++;
            final Edge[] edges = graph.outgoingEdgesOf(startBuild).toArray(new Edge[0]);
            Arrays.sort(edges, new Comparator<Edge>() {
                @Override
                public int compare(Edge edge1, Edge edge2) {
                    return Double.compare(edge1.getTarget().x, edge2.getTarget().x);
                }
            });
            for (int i = 0; i < edges.length; i++) {
                x = transform(graph, (Vertex<AbstractBuild<?, ?>>) edges[i].getTarget(), x, y, traveredMap);
            }
        } else {
            x++;
        }
        return x;
    }

    /**
     * call graphviz tools for help us transform the build execution graph
     *
     * @param graph the buld execution graph
     */
    private void graphvizLayout(DirectedGraph<Vertex<AbstractBuild<?, ?>>, Edge> graph) {
        final Map<String, Vertex<AbstractBuild<?, ?>>> vertexMap = new HashMap<String, Vertex<AbstractBuild<?, ?>>>();
        for (Vertex<AbstractBuild<?, ?>> vertex : graph.vertexSet()) {
            vertexMap.put(vertex.build.toString(), vertex);
        }
        File tmpGraphVizPlainTextFile = null;
        try {
            tmpGraphVizPlainTextFile = new File(graphvizLayoutPlain(exportDOT(graph)));
            for (String line : Unix4j.cat(tmpGraphVizPlainTextFile).grep("node").toStringList()) {
                final Pattern p = Pattern.compile("node \"(.+)\" ([-+]?[0-9]*\\.?[0-9]+) ([-+]?[0-9]*\\.?[0-9]+)");
                final Matcher m = p.matcher(line);
                if (m.find()) {
                    final Vertex<AbstractBuild<?, ?>> vertex = vertexMap.get(m.group(1));
                    vertex.setX(Double.parseDouble(m.group(2)));
                    //vertex.setY(Double.parseDouble(m.group(3)));
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "IOException ", e);
        } finally {
            if (tmpGraphVizPlainTextFile != null) {
                try {
                    tmpGraphVizPlainTextFile.delete();
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Exception ", e);
                }
            }
        }
    }

    /**
     * make use of graphviz to layout and generate plain-text output
     *
     * @param dotFilePath the Dot file path
     * @return the absolute path of plain-text of layout by graphviz
     * @throws IOException
     */
    private String graphvizLayoutPlain(String dotFilePath) throws IOException {
        final String dotCommand = Functions.isWindows() ? "dot.exe" : "dot";
        final Jenkins jenkins = Jenkins.getInstance();
        File dotFile = null;
        File graphvizPlainTextFile = null;
        OutputStream output = null;
        InputStream input = null;
        try {
            dotFile = new File(dotFilePath);
            graphvizPlainTextFile = File.createTempFile("tmp_plain", ".txt");
            output = new FileOutputStream(graphvizPlainTextFile);
            input = new FileInputStream(dotFile);
            if (jenkins != null) {
                final Launcher launcher = jenkins.createLauncher(new LogTaskListener(LOGGER, Level.CONFIG));
                launcher.launch()
                        .cmds(dotCommand, "-Tplain", "-Gcharset=UTF-8", "-q1")
                        .stdin(input)
                        .stdout(output)
                        .start().join();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception: ", e);
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (dotFile != null) {
                try {
                    dotFile.delete();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return graphvizPlainTextFile.getAbsolutePath();
    }

    /**
     * export build execution graph into dot file
     *
     * @param graph build execution graph
     * @return the absolute path of dot file exported
     */
    private String exportDOT(DirectedGraph<Vertex<AbstractBuild<?, ?>>, Edge> graph) {
        Writer writer = null;
        File dotFile = null;
        try {
            dotFile = File.createTempFile("tmp", ".dot");
            writer = new OutputStreamWriter(new FileOutputStream(dotFile), "UTF-8");
            final DOTExporter exporter = new DOTExporter(new StringNameProvider(), null, null);
            exporter.export(writer, graph);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "IOException", e);
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "IOException ", e);
            }

        }
        if (dotFile != null) {
            return dotFile.getAbsolutePath();
        } else {
            return null;
        }
    }

    /**
     * Gain the whole build execution graph
     *
     * @param currentVertex currentVertex
     * @throws ExecutionException
     * @throws InterruptedException
     */
    private void computeGraphFrom(Vertex<AbstractBuild<?, ?>> currentVertex) throws ExecutionException, InterruptedException {
        for (DownStreamRunDeclarer declarer : DownStreamRunDeclarer.all()) {
            if (declarer instanceof SubProjectsDownStreamRunDeclarer) {
                final List<Run> runs = declarer.getDownStream(currentVertex.getBuild());
                goThroughBuilds(runs);
                break;
            }
        }

        for (DownStreamRunDeclarer declarer : DownStreamRunDeclarer.all()) {
            if (declarer instanceof FlowDownStreamRunDeclarer) {
                final List<Run> runs = declarer.getDownStream((Run) currentVertex.getBuild());
                for (Run r : runs) {
                    if (r != null) {
                        final AbstractBuild<?, ?> next = (AbstractBuild<?, ?>) r;
                        final Vertex<AbstractBuild<?, ?>> newVertex = new Vertex<AbstractBuild<?, ?>>(next, 0);
                        graph.addVertex(newVertex); // ignore if already added
                        graph.addEdge(currentVertex, newVertex, new Edge(currentVertex, newVertex));
                        computeGraphFrom(newVertex);
                    }
                }
                break;
            }
        }

        for (DownStreamRunDeclarer declarer : DownStreamRunDeclarer.all()) {
            if (declarer instanceof UpstreamCauseDonwStreamRunDeclarer) {
                final List<Run> runs = declarer.getDownStream(currentVertex.getBuild());
                goThroughBuilds(runs);
                break;
            }
        }
    }

    /**
     * Recursive traverse the build's downstream for gain the build execution graph
     *
     * @param runs builds form Runner
     * @throws ExecutionException
     * @throws InterruptedException
     */
    private void goThroughBuilds(List<Run> runs) throws ExecutionException, InterruptedException {
        for (Run r : runs) {
            if (r != null) {
                final AbstractBuild<?, ?> next = (AbstractBuild<?, ?>) r;
                final Vertex<AbstractBuild<?, ?>> newVertex = new Vertex<AbstractBuild<?, ?>>(next, 0);
                graph.addVertex(newVertex); // ignore if already added
                final List<List<Vertex<AbstractBuild<?, ?>>>> allPaths = findAllPaths(this.graph, start, start);
                for (List<Vertex<AbstractBuild<?, ?>>> path : allPaths) {
                    final Vertex<AbstractBuild<?, ?>> endVertex = path.get(path.size() - 1);
                    graph.addEdge(endVertex, newVertex, new Edge(endVertex, newVertex));
                }
                computeGraphFrom(newVertex);
            }
        }
    }

    /**
     * Gain all of path of a build in the build execution graph
     *
     * @param graph    gprah
     * @param original original
     * @param start    start
     * @return all of path found
     */
    private List<List<Vertex<AbstractBuild<?, ?>>>> findAllPaths(DirectedGraph<Vertex<AbstractBuild<?, ?>>, Edge> graph,
                                                                 Vertex<AbstractBuild<?, ?>> original, Vertex<AbstractBuild<?, ?>> start) {
        final List<List<Vertex<AbstractBuild<?, ?>>>> allPaths = new LinkedList<List<Vertex<AbstractBuild<?, ?>>>>();
        if (graph.outDegreeOf(start) == 0) {
            // base case
            final List<Vertex<AbstractBuild<?, ?>>> singlePath = new LinkedList<Vertex<AbstractBuild<?, ?>>>();
            singlePath.add(start);
            allPaths.add(singlePath);
        } else {
            boolean cyclicFlag = false;
            for (Edge edge : graph.outgoingEdgesOf(start)) {
                if (edge.getTarget() == original) {
                    cyclicFlag = true;
                    break;
                }
            }
            if (cyclicFlag) {
                graph.removeAllEdges(start, original);
            }
            for (Edge edge : graph.outgoingEdgesOf(start)) {
                final Vertex<AbstractBuild<?, ?>> next = edge.getTarget();
                final List<List<Vertex<AbstractBuild<?, ?>>>> allPathsFromTarget = findAllPaths(graph, original, next);
                for (List<Vertex<AbstractBuild<?, ?>>> path : allPathsFromTarget) {
                    path.add(0, start);
                }
                allPaths.addAll(allPathsFromTarget);
            }
        }
        return allPaths;
    }

    /**
     * The structure for storing the transform info of build
     */
    static class Position {
        /**
         * transform info of build on the pipeline view
         */
        int x, y;

        /**
         * The structure for build transform info
         *
         * @param colNum which column the build would be placed on
         * @param rowNum which row the buld would be placed on
         */
        public Position(int colNum, int rowNum) {
            this.x = colNum;
            this.y = rowNum;
        }
    }

    /**
     * For the build execution graph
     *
     * @param <T>
     */
    static class Vertex<T> {
        /**
         * the structure of build and its transform infor
         *
         * @param build build
         * @param x     column number
         */
        public Vertex(T build, double x) {
            this.build = build;
            this.x = x;
        }

        public void setX(double x) {
            this.x = x;
        }

        public T getBuild() {
            return build;
        }

        /**
         * for storing the number of column in transform
         */
        double x;

        /**
         * Build
         */
        T build;
    }

    /**
     * For build execution graph: downstream relationship between builds
     */
    static class Edge {
        /**
         * Parent Build
         */
        private Vertex<AbstractBuild<?, ?>> source;

        /**
         * Child Build
         */
        private Vertex<AbstractBuild<?, ?>> target;

        /**
         * Build Eexecution relationship
         *
         * @param source parent build
         * @param target child build
         */
        public Edge(Vertex<AbstractBuild<?, ?>> source, Vertex<AbstractBuild<?, ?>> target) {
            this.source = source;
            this.target = target;
        }

        public Vertex<AbstractBuild<?, ?>> getSource() {
            return source;
        }

        public Vertex<AbstractBuild<?, ?>> getTarget() {
            return target;
        }

        @Override
        public String toString() {
            return source.toString() + " -> " + target.toString();
        }
    }
}