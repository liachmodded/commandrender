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
package com.github.liachmodded.commandrender;

import static java.util.Objects.requireNonNull;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.mojang.datafixers.util.Pair;
import guru.nidi.graphviz.attribute.Attributes;
import guru.nidi.graphviz.attribute.Color;
import guru.nidi.graphviz.attribute.GraphAttr;
import guru.nidi.graphviz.attribute.Label;
import guru.nidi.graphviz.attribute.LinkAttr;
import guru.nidi.graphviz.attribute.Shape;
import guru.nidi.graphviz.attribute.Style;
import guru.nidi.graphviz.engine.Engine;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.engine.GraphvizCmdLineEngine;
import guru.nidi.graphviz.engine.GraphvizException;
import guru.nidi.graphviz.engine.GraphvizV8Engine;
import guru.nidi.graphviz.engine.Renderer;
import guru.nidi.graphviz.model.CommandGraph;
import guru.nidi.graphviz.model.Factory;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.model.MutableNode;
import guru.nidi.graphviz.model.Writer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

public final class Dumper<S> {

    private static final Logger LOGGER = LogManager.getLogger("command render dumper");
    private final CommandDispatcher<S> dispatcher;
    private final Map<CommandNode<S>, MutableNode> nodeMap;

    public Dumper(final CommandDispatcher<S> dispatcher) {
        this.dispatcher = dispatcher;
        this.nodeMap = new HashMap<>();

        calc();
    }

    void calc() {
        final RootCommandNode<S> root = dispatcher.getRoot();

        //        final Map<Integer, MapAttributes<ForGraph>> levels = new HashMap<>();

        final MutableNode rootGraphNode = Factory.mutNode(Label.of(" "));
        rootGraphNode.add(Shape.CIRCLE);
        rootGraphNode.add(Label.of(""));
        nodeMap.put(root, rootGraphNode);

        final CommandGraph mainGraph = new CommandGraph();
        mainGraph.setDirected(true);
        mainGraph.graphAttrs().add(Attributes.attr("rankdir", "LR"));
        //        graph.graphAttrs().add(Attributes.attr("nslimit", 1));
        //        graph.graphAttrs().add(Attributes.attr("nslimit1", 1));
        //        graph.graphAttrs().add(Attributes.attr("maxiter", 1));
        mainGraph.graphAttrs().add(GraphAttr.clusterRank(GraphAttr.ClusterMode.LOCAL));
        mainGraph.graphAttrs().add(GraphAttr.splines(GraphAttr.SplineMode.ORTHO));
        mainGraph.add(rootGraphNode);

        final Collection<MutableGraph> clusters = new ArrayList<>();

        for (final CommandNode<S> firstLiteral : root.getChildren()) {
//            if (!firstLiteral.getName().equals("execute") && !firstLiteral.getName().equals("data")) {
//                continue;
//            }
            final Queue<Pair<CommandNode<S>, String>> queue = new ArrayDeque<>();
            queue.add(new Pair<>(firstLiteral, firstLiteral.getName()));
            final MutableNode firstLiteralGraphNode = Factory.mutNode(Label.of(firstLiteral.getName()));
            nodeMap.put(firstLiteral, firstLiteralGraphNode);
            //            rootGraphNode.links().add(rootGraphNode.linkTo(firstLiteralGraphNode));

            final MutableGraph graph = Factory.mutGraph(firstLiteral.getName()).setDirected(true).setCluster(true);
            clusters.add(graph);
            graph.graphAttrs().add(Label.of(firstLiteral.getName()));

            firstLiteralGraphNode.add(Label.of(firstLiteral.getUsageText()));

            if (firstLiteral.getCommand() != null) {
                firstLiteralGraphNode.add(Color.RED);
            }

            if (firstLiteral.isFork()) {
                firstLiteralGraphNode.add(Style.DASHED);
            }

            graph.add(firstLiteralGraphNode);

            while (!queue.isEmpty()) {
                final Pair<CommandNode<S>, String> pair = queue.remove();
                final CommandNode<S> commandNode = pair.getFirst();
                LOGGER.info("Visiting \"{}\"", pair.getSecond());
                final MutableNode graphNode = requireNonNull(nodeMap.get(commandNode));

                for (CommandNode<S> child : commandNode.getChildren()) {
                    final String title = pair.getSecond() + " " + child.getName();
                    final MutableNode childGraphNode = Factory.mutNode(Label.of(title));

                    childGraphNode.add(Label.of(child.getUsageText()));
                    childGraphNode.add(child instanceof ArgumentCommandNode ? Shape.DIAMOND : Shape.RECTANGLE);

                    if (child.getCommand() != null) {
                        childGraphNode.add(Color.RED);
                    }

                    if (child.isFork()) {
                        childGraphNode.add(Style.DASHED);
                    }

                    graph.add(childGraphNode);
                    graphNode.addLink(childGraphNode.linkTo());
                    queue.add(new Pair<>(child, title));
                    // graphNode.addLink(childGraphNode);
                    nodeMap.put(child, childGraphNode);
                }
            }
        }


        for (MutableGraph cluster : clusters) {
            cluster.addTo(mainGraph);
        }

        for (CommandNode<S> node : root.getChildren()) {
            MutableNode graphNode = nodeMap.get(node);
            if (graphNode != null) {
                rootGraphNode.addLink(graphNode.linkTo());
            }
        }

        //        try {
        //            MutableGraph mute = Parser.read(new File("testdot.dot"));
        //            LOGGER.info("Fine");
        //        } catch (IOException ex) {
        //
        //        }

        for (Map.Entry<CommandNode<S>, MutableNode> entry : nodeMap.entrySet()) {
            final CommandNode<S> commandNode = entry.getKey();
            final MutableNode graphNode = entry.getValue();

            final CommandNode<S> redirect = commandNode.getRedirect();
            if (redirect != null) {
                mainGraph.getExtraLinks().add(graphNode.linkTo(nodeMap.get(redirect)).add(Style.DASHED).add(LinkAttr.CONSTRAINT_NOT));
            }
        }

        // https://github.com/dddjava/Jig/commit/7605f89d23a04fc3ff25abe106220194b57847ca
        // Apache 2.0
//        GraphvizCmdLineEngine graphvizCmdLineEngine = new GraphvizCmdLineEngine();
//        try {
//            Method doInit = GraphvizCmdLineEngine.class.getDeclaredMethod("doInit");
//            doInit.setAccessible(true);
//            doInit.invoke(graphvizCmdLineEngine);
//        } catch (NoSuchMethodException | IllegalAccessException e) {
//            throw new IllegalStateException(e);
//        } catch (InvocationTargetException e) {
//            if (e.getTargetException() instanceof GraphvizException) {
//                throw new IllegalStateException(e.getTargetException().getMessage());
//            }
//            throw new IllegalStateException(e);
//        }
//
//        Graphviz.useEngine(graphvizCmdLineEngine);
//        Graphviz.useEngine(new GraphvizV8Engine());
        // end apache 2.0
        LOGGER.info("Engine setup!");

        Renderer renderer = Graphviz.fromString(new Writer(mainGraph).serialize()).engine(Engine.DOT).render(Format.SVG);
        try {
            renderer.toFile(new File("dump.svg"));
            LOGGER.info("Dumped command image");
        } catch (IOException ex) {
            LOGGER.error("Cannot dump rendered file!");
        }
    }

    public static Dumper<ServerCommandSource> of(CommandManager manager) {
        return new Dumper<>(manager.getDispatcher());
    }
}
