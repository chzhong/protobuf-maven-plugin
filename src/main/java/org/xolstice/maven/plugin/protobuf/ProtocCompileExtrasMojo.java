package org.xolstice.maven.plugin.protobuf;

/*
 * Copyright (c) 2019 Maven Protocol Buffers Plugin Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.commons.lang3.ObjectUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.toolchain.Toolchain;

import java.io.File;
import java.util.List;

/**
 * This mojo executes the {@code protoc} compiler with the specified plugin
 * executable to generate main sources from protocol buffer definitions.
 * It also searches dependency artifacts for {@code .proto} files and
 * includes them in the {@code proto_path} so that they can be referenced.
 * Finally, it adds the {@code .proto} files to the project as resources so
 * that they are included in the final artifact.
 *
 * @since 0.4.1
 */
@Mojo(
        name = "compile-extras",
        defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        threadSafe = true
)
public final class ProtocCompileExtrasMojo extends AbstractProtocCompileMojo {

    public static final class ExtraPlugin {

        String pluginId;

        File outputBaseDirectory;

        File outputDirectory;

        String pluginExecutable;

        String pluginParameter;

        String pluginToolchain;

        String pluginTool;

        String pluginArtifact;

        File getOutputDirectory(File extraOutputBaseDirectory) {
            File outputDirectory = this.outputDirectory;
            if (outputDirectory == null) {
                outputDirectory = new File(ObjectUtils.firstNonNull(outputBaseDirectory, extraOutputBaseDirectory), pluginId);
            }
            return outputDirectory;
        }

        protected void addProtocBuilderParameters(final Protoc.Builder protocBuilder, AbstractProtocCompileMojo parent, File extraOutputBaseDirectory) {
            protocBuilder.setNativePluginId(pluginId);
            if (pluginToolchain != null && pluginTool != null) {
                //get toolchain from context
                final Toolchain tc = parent.toolchainManager.getToolchainFromBuildContext(pluginToolchain, parent.session);
                if (tc != null) {
                    parent.getLog().info("Toolchain in protobuf-maven-plugin: " + tc);
                    //when the executable to use is explicitly set by user in mojo's parameter, ignore toolchains.
                    if (pluginExecutable != null) {
                        parent.getLog().warn("Toolchains are ignored, 'pluginExecutable' parameter is set to " + pluginExecutable);
                    } else {
                        //assign the path to executable from toolchains
                        pluginExecutable = tc.findTool(pluginTool);
                    }
                }
            }
            if (pluginExecutable == null && pluginArtifact != null) {
                final Artifact artifact = parent.createDependencyArtifact(pluginArtifact);
                final File file = parent.resolveBinaryArtifact(artifact);
                pluginExecutable = file.getAbsolutePath();
            }
            if (pluginExecutable != null) {
                protocBuilder.setNativePluginExecutable(pluginExecutable);
            }
            if (pluginParameter != null) {
                protocBuilder.setNativePluginParameter(pluginParameter);
            }
            protocBuilder.setCustomOutputDirectory(getOutputDirectory(extraOutputBaseDirectory));
        }

    }

    /**
     * This is the base directory for the generated code.
     */
    @Parameter(
            required = true,
            readonly = true,
            defaultValue = "${project.build.directory}/generated-sources/protobuf"
    )
    private File outputBaseDirectory;

    @Parameter
    private List<ExtraPlugin> extraPlugins;

    private ExtraPlugin currentPlugin;

    protected void addProtocBuilderParameters(final Protoc.Builder protocBuilder) {
        super.addProtocBuilderParameters(protocBuilder);
        currentPlugin.addProtocBuilderParameters(protocBuilder, this, this.outputBaseDirectory);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (null == extraPlugins || extraPlugins.isEmpty()) {
            getLog().info("No extra plugins to execute.");
            return;
        }
        for (ExtraPlugin extraPlugin : extraPlugins) {
            currentPlugin = extraPlugin;
            super.execute();
            currentPlugin = null;
        }
    }

    @Override
    protected File getOutputDirectory() {
        return currentPlugin.getOutputDirectory(outputBaseDirectory);
    }

    public List<ExtraPlugin> getExtraPlugins() {
        return extraPlugins;
    }
}
