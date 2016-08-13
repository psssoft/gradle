/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;

/**
 * Adapts {@link ResolutionResultBuilder}, which takes care of assembling the resolution result, to a {@link DependencyGraphVisitor}.
 */
public class ResolutionResultDependencyGraphVisitor implements DependencyGraphVisitor {
    private final ResolutionResultBuilder newModelBuilder;

    public ResolutionResultDependencyGraphVisitor(ResolutionResultBuilder newModelBuilder) {
        this.newModelBuilder = newModelBuilder;
    }

    public void start(DependencyGraphNode root) {
        newModelBuilder.start(root.getOwner().getId(), root.getOwner().getComponentId());
    }

    public void visitNode(DependencyGraphNode resolvedConfiguration) {
        newModelBuilder.resolvedModuleVersion(resolvedConfiguration.getOwner());
    }

    public void visitEdge(DependencyGraphNode resolvedConfiguration) {
        newModelBuilder.resolvedConfiguration(resolvedConfiguration.getOwner().getId(), resolvedConfiguration.getOutgoingEdges());
    }

    public void finish(DependencyGraphNode root) {

    }
}
