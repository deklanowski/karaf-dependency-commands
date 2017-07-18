package org.deklanowski.karaf.commands.dependency.internal;

import com.google.common.base.Preconditions;
import com.google.common.graph.*;
import com.google.common.graph.Graph;

import java.util.*;

/**
 * This uses a BFS topological sort with graph depth tracking to sort dependencies into
 * levels. Levels identify those sets of dependencies that can be independently handled.
 *
 * The BFS algorithm is according to Kahn (1962)
 *
 * The slick solution to graph depth/level tracking courtesy of
 * https://stackoverflow.com/questions/31247634/how-to-keep-track-of-depth-in-breadth-first-search/31248992#31248992
 *
 * I use {@link Graph}, the input graph instance must be a DAG.
 *
 * @author deklanowski
 * @since June 2017
 * @param <T>
 */
public class DependencySorter<T> {

    /**
     * Maps levels to nodes at that level. Higher level dependencies
     * must be addressed first.
     */
    private Map<Integer, Set<T>> levelMap = new HashMap<>();


    /**
     * @param graph dependency graph, must be a DAG
     * @return topologically sorted nodes according to BFS
     */
    public List<T> sort(Graph<T> graph) {

        Preconditions.checkArgument(graph.isDirected() && !Graphs.hasCycle(graph), "Input graph must be directed and acyclic.");

        // clear state
        this.levelMap = new HashMap<>();

        // sorted output
        List<T> result = new ArrayList<>();

        Queue<T> queue = new LinkedList<>();

        // We don't mutate the graph itself so we
        // track visitations and attendant changes to
        // in-degree in a separate map.
        //
        Map<T, Integer> nodeDegree = new HashMap<>();

        // Top of the dependency tree
        int level = 1;

        // Find starter nodes
        for (T node : graph.nodes()) {
            int inDegree = graph.inDegree(node);
            nodeDegree.put(node, inDegree);
            if (inDegree == 0) {
                queue.add(node);
                result.add(node);
            }
        }

        queue.add(null); // this marks end of level 1, which contains all starter nodes
        System.out.printf("Level %d queue=%s\n",level,queue);
        addToLevelMap(level, queue);

        while (!queue.isEmpty()) {
            T node = queue.poll();

            // Check whether we need to increment the level
            if (node == null) {
                queue.add(null);
                if (queue.peek() == null) {
                    System.out.println("Two consecutive nulls encountered, all nodes visited.");
                    break;
                } else {
                    level++;
                    System.out.printf("Level %d queue=%s\n",level,queue);
                    addToLevelMap(level, queue);
                    continue;
                }
            }


            Set<T> successors = graph.successors(node);

            System.out.printf("node:%s successors :%s\n",node,successors);

            for (T successor : successors) {

                int degree = nodeDegree.get(successor);

                nodeDegree.put(successor, (degree == 0 ? 0 : degree-1));

                if (shouldAddToQueue(degree)) {
                    System.out.printf("Adding %s to queue and output\n",successor);
                    queue.offer(successor);
                    result.add(successor);
                }
            }
        }

        return result;
    }

    /**
     * Add nodes to level map, remove marker nulls.
     * @param level graph depth
     * @param queue current node queue
     */
    private void addToLevelMap(int level, Queue<T> queue) {
        Set<T> nodes = new HashSet<>(queue);
        nodes.remove(null); // remove our marker nulls
        levelMap.put(level, nodes);
    }


    /**
     * Call this directly after {@link #sort(Graph)} to get level view of
     * nodes.
     * @return level to nodes map
     */
    public Map<Integer, Set<T>> getLevelMap() {
        return this.levelMap;
    }

    /**
     * @param inDegree the degree of the node being processed
     * @return true if decrementing the degree gives an in-degree of 0
     */
    private boolean shouldAddToQueue(int inDegree) {
        return (inDegree - 1) == 0;
    }


    public static void main(String[] args) {
        final MutableGraph<Integer> g = GraphBuilder.directed().allowsSelfLoops(false).build();


        g.putEdge(1, 2);
        g.putEdge(1, 3);
        g.putEdge(2, 4);
        g.putEdge(2, 5);
        g.putEdge(3, 6);
        g.putEdge(3, 7);
        g.putEdge(1, 7);
        g.putEdge(2, 7);
        g.putEdge(8, 3);
        g.putEdge(8, 5);
        g.putEdge(8, 9);
        g.putEdge(9, 10);
        g.putEdge(10, 11);
        g.putEdge(6, 12);
        g.putEdge(11, 6);


        DependencySorter<Integer> dependencySorter = new DependencySorter<>();
        List<Integer> list = dependencySorter.sort(g);

        System.out.println("Topologically sorted, for dependency build order reverse the list");
        System.out.println(list);
        System.out.println();


        // debug
        generateDotOutput(g);


        System.out.println("\nLevel Map:");

        for (Map.Entry<Integer,Set<Integer>> entry : dependencySorter.getLevelMap().entrySet()) {
            System.out.printf("%d -> %s\n",entry.getKey(),entry.getValue());
        }
    }


    /**
     * Simple DOT output for GraphViz. Paste this into http://www.webgraphviz.com/
     * to help you confirm the algorithm.
     * @param g the graph instance.
     */
    private static void generateDotOutput(MutableGraph<Integer> g) {
        System.out.println("digraph G {");
        for (EndpointPair<Integer> pair : g.edges()) {
            System.out.printf("\t%d -> %d;\n",pair.source(),pair.target());
        }
        System.out.println("}");
    }
}
