/*
 * Copyright (C) 2017 Seoul National University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.snu.vortex.compiler.optimizer.passes;

import edu.snu.vortex.common.dag.DAG;
import edu.snu.vortex.compiler.ir.IREdge;
import edu.snu.vortex.compiler.ir.IRVertex;
import edu.snu.vortex.compiler.ir.attribute.Attribute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static edu.snu.vortex.compiler.ir.attribute.Attribute.Memory;

/**
 * Default method of partitioning an IR DAG into stages.
 * We traverse the DAG topologically to observe each vertex if it can be added to a stage or if it should be assigned
 * to a new stage. We filter out the candidate incoming edges to connect to an existing stage, and if it exists, we
 * connect it to the stage, and otherwise we don't.
 */
public final class DefaultStagePartitioningPass implements Pass {
  @Override
  public DAG<IRVertex, IREdge> process(final DAG<IRVertex, IREdge> irDAG) {
    final AtomicInteger stageNum = new AtomicInteger(0);
    final List<List<IRVertex>> vertexListForEachStage = groupVerticesByStage(irDAG);
    vertexListForEachStage.forEach(stageVertices -> {
      stageVertices.forEach(irVertex -> irVertex.setAttr(Attribute.IntegerKey.StageId, stageNum.get()));
      stageNum.getAndIncrement();
    });
    return irDAG;
  }

  /**
   * This method traverses the IR DAG to group each of the vertices by stages.
   * @param irDAG to traverse.
   * @return List of groups of vertices that are each divided by stages.
   */
  private List<List<IRVertex>> groupVerticesByStage(final DAG<IRVertex, IREdge> irDAG) {
    // Data structures used for stage partitioning.
    final HashMap<IRVertex, Integer> vertexStageNumHashMap = new HashMap<>();
    final List<List<IRVertex>> vertexListForEachStage = new ArrayList<>();
    final AtomicInteger stageNumber = new AtomicInteger(0);
    final List<Integer> dependentStagesList = new ArrayList<>();

    // First, traverse the DAG topologically to add each vertices to a list associated with each of the stage number.
    irDAG.topologicalDo(vertex -> {
      final List<IREdge> inEdges = irDAG.getIncomingEdgesOf(vertex);
      final Optional<List<IREdge>> inEdgeList = (inEdges == null || inEdges.isEmpty())
              ? Optional.empty() : Optional.of(inEdges);

      if (!inEdgeList.isPresent()) { // If Source vertex
        createNewStage(vertex, vertexStageNumHashMap, stageNumber, vertexListForEachStage);
      } else {
        // Filter candidate incoming edges that can be included in a stage with the vertex.
        final Optional<List<IREdge>> inEdgesForStage = inEdgeList.map(e -> e.stream()
                .filter(edge -> edge.getType().equals(IREdge.Type.OneToOne)) // One to one edges
                .filter(edge -> edge.getAttr(Attribute.Key.ChannelDataPlacement).equals(Memory))// Memory data placement
                .filter(edge -> edge.getSrc().getAttr(Attribute.Key.Placement)
                        .equals(edge.getDst().getAttr(Attribute.Key.Placement))) //Src and Dst same placement
                // Src that is already included in a stage
                .filter(edge -> vertexStageNumHashMap.containsKey(edge.getSrc()))
                // Others don't depend on the candidate stage.
                .filter(edge -> !dependentStagesList.contains(vertexStageNumHashMap.get(edge.getSrc())))
                .collect(Collectors.toList()));
        // Choose one to connect out of the candidates. We want to connect the vertex to a single stage.
        final Optional<IREdge> edgeToConnect = inEdgesForStage.map(edges -> edges.stream().findAny())
                .orElse(Optional.empty());

        // Mark stages that other stages depend on
        inEdgeList.ifPresent(edges -> edges.stream()
                .filter(e -> !e.equals(edgeToConnect.orElse(null))) // e never equals null
                .forEach(inEdge -> dependentStagesList.add(vertexStageNumHashMap.get(inEdge.getSrc()))));

        if (!inEdgesForStage.isPresent() || inEdgesForStage.get().isEmpty() || !edgeToConnect.isPresent()) {
          // when we cannot connect vertex in other stages
          createNewStage(vertex, vertexStageNumHashMap, stageNumber, vertexListForEachStage);
        } else {
          // otherwise connect with a stage.
          final IRVertex irVertexToConnect = edgeToConnect.get().getSrc();
          vertexStageNumHashMap.put(vertex, vertexStageNumHashMap.get(irVertexToConnect));
          final Optional<List<IRVertex>> listOfIRVerticesOfTheStage =
                  vertexListForEachStage.stream().filter(l -> l.contains(irVertexToConnect)).findFirst();
          listOfIRVerticesOfTheStage.ifPresent(lst -> {
            vertexListForEachStage.remove(lst);
            lst.add(vertex);
            vertexListForEachStage.add(lst);
          });
        }
      }
    });
    return vertexListForEachStage;
  }

  /**
   * Creates a new stage.
   * @param irVertex the vertex which begins the stage.
   * @param vertexStageNumHashMap to keep track of vertex and its stage number.
   * @param stageNumber to atomically number stages.
   * @param vertexListForEachStage to group each vertex lists for each stages.
   */
  private static void createNewStage(final IRVertex irVertex, final HashMap<IRVertex, Integer> vertexStageNumHashMap,
                                     final AtomicInteger stageNumber,
                                     final List<List<IRVertex>> vertexListForEachStage) {
    vertexStageNumHashMap.put(irVertex, stageNumber.getAndIncrement());
    final List<IRVertex> newList = new ArrayList<>();
    newList.add(irVertex);
    vertexListForEachStage.add(newList);
  }
}