/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <http://unlicense.org/>
 */
package guru.nidi.graphviz.model;

import com.google.common.collect.Lists;
import guru.nidi.graphviz.attribute.Label;
import guru.nidi.graphviz.attribute.SimpleLabel;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Writer {

    private final CommandGraph graph;
    private final StringBuilder str;

    public Writer(CommandGraph graph) {
        this.graph = graph;
        str = new StringBuilder();
    }

    public String serialize() {
        graph(graph, true);
        return str.toString();
    }

    private void graph(MutableGraph graph, boolean toplevel) {
        graphInit(graph, toplevel);
        graphAttrs(graph);

        final List<MutableNode> nodes = Lists.newArrayList(graph.nodes);
        final List<MutableGraph> graphs = Lists.newArrayList(graph.subgraphs);//new ArrayList<>();
        //        final Collection<LinkSource> linkSources = linkedNodes(graph.nodes);
        //        linkSources.addAll(linkedNodes(graph.subgraphs));
        //        for (final LinkSource linkSource : linkSources) {
        //            if (linkSource instanceof MutableNode) {
        //                final MutableNode node = (MutableNode) linkSource;
        //                final int i = indexOfName(nodes, node.name);
        //                if (i < 0) {
        //                    nodes.add(node);
        //                } else {
        //                    nodes.set(i, node.copy().merge(nodes.get(i)));
        //                }
        //            } else {
        //                graphs.add((MutableGraph) linkSource);
        //            }
        //        }

        nodes(graph, nodes);
        graphs(graphs, nodes);

        edges(nodes);
        edges(graphs);

        if (graph instanceof CommandGraph) {
            links(((CommandGraph) graph).extraLinks);
        }

        str.append('}');
    }

    private void graphAttrs(MutableGraph graph) {
        attributes("graph", graph.graphAttrs);
        attributes("node", graph.nodeAttrs);
        attributes("edge", graph.linkAttrs);
    }

    private void graphInit(MutableGraph graph, boolean toplevel) {
        if (toplevel) {
            str.append(graph.strict ? "strict " : "").append(graph.directed ? "digraph " : "graph ");
            if (!graph.name.isEmpty()) {
                str.append(SimpleLabel.of(graph.name).serialized()).append(' ');
            }
        } else if (!graph.name.isEmpty() || graph.cluster) {
            str.append("subgraph ")
                    .append(Label.of((graph.cluster ? "cluster_" : "") + graph.name).serialized())
                    .append(' ');
        }
        str.append("{\n");
    }

    private int indexOfName(List<MutableNode> nodes, Label name) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).name.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private void attributes(String name, MutableAttributed<?, ?> attributed) {
        if (!attributed.isEmpty()) {
            str.append(name);
            attrs(attributed);
            str.append('\n');
        }
    }

    private Collection<LinkSource> linkedNodes(Collection<? extends LinkSource> nodes) {
        final Set<LinkSource> visited = new LinkedHashSet<>();
        for (final LinkSource node : nodes) {
            linkedNodes(node, visited);
        }
        return visited;
    }

    private void linkedNodes(LinkSource linkSource, Set<LinkSource> visited) {
        if (!visited.contains(linkSource)) {
            visited.add(linkSource);
            for (final Link link : linkSource.links()) {
                linkedNodes(link.to.asLinkSource(), visited);
            }
        }
    }

    private void nodes(MutableGraph graph, List<MutableNode> nodes) {
        for (final MutableNode node : nodes) {
            if (!node.attributes.isEmpty()
                    || (graph.nodes.contains(node) && node.links.isEmpty() && !isLinked(node, nodes))) {
                node(node);
                str.append('\n');
            }
        }
    }

    private void node(MutableNode node) {
        str.append(node.name.serialized());
        attrs(node.attributes);
    }

    private boolean isLinked(MutableNode node, List<MutableNode> nodes) {
        for (final MutableNode m : nodes) {
            for (final Link link : m.links) {
                if (isNode(link.to, node)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isLinked(MutableGraph graph, List<? extends LinkSource> linkSources) {
        for (final LinkSource linkSource : linkSources) {
            for (final Link link : linkSource.links()) {
                if (link.to.equals(graph)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isNode(LinkTarget target, MutableNode node) {
        return target == node || (target instanceof ImmutablePortNode && ((ImmutablePortNode) target).node() == node);
    }

    private void graphs(List<MutableGraph> graphs, List<MutableNode> nodes) {
        for (final MutableGraph graph : graphs) {
            if (graph.links.isEmpty() && !isLinked(graph, nodes) && !isLinked(graph, graphs)) {
                graph(graph, false);
                str.append('\n');
            }
        }
    }

    private void edges(List<? extends LinkSource> linkSources) {
        for (final LinkSource linkSource : linkSources) {
            links(linkSource.links());
        }
    }

    private void links(List<Link> links) {
        for (final Link link : links) {
            linkTarget(link.from);
            str.append(graph.directed ? " -> " : " -- ");
            linkTarget(link.to);
            attrs(link.attributes);
            str.append('\n');
        }
    }

    private void linkTarget(Object linkable) {
        if (linkable instanceof MutableNode) {
            str.append(((MutableNode) linkable).name.serialized());
        } else if (linkable instanceof ImmutablePortNode) {
            port((ImmutablePortNode) linkable);
        } else if (linkable instanceof MutableGraph) {
            graph((MutableGraph) linkable, false);
        } else {
            throw new IllegalStateException("unexpected link target " + linkable);
        }
    }

    private void port(ImmutablePortNode portNode) {
        str.append(portNode.name().serialized());
        final String record = portNode.port().record();
        if (record != null) {
            str.append(':').append(SimpleLabel.of(record).serialized());
        }
        final Compass compass = portNode.port().compass();
        if (compass != null) {
            str.append(':').append(compass.value);
        }
    }

    private void attrs(MutableAttributed<?, ?> attrs) {
        if (!attrs.isEmpty()) {
            str.append(" [");
            boolean first = true;
            for (final Map.Entry<String, Object> attr : attrs) {
                if (first) {
                    first = false;
                } else {
                    str.append(',');
                }
                attr(attr.getKey(), attr.getValue());
            }
            str.append(']');
        }
    }

    private void attr(String key, Object value) {
        str.append(SimpleLabel.of(key).serialized())
                .append('=')
                .append(SimpleLabel.of(value).serialized());
    }

}
