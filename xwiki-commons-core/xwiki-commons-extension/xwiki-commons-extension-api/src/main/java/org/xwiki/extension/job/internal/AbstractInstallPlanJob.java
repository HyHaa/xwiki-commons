/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.extension.job.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.extension.CoreExtension;
import org.xwiki.extension.DefaultExtensionDependency;
import org.xwiki.extension.Extension;
import org.xwiki.extension.ExtensionDependency;
import org.xwiki.extension.ExtensionId;
import org.xwiki.extension.InstallException;
import org.xwiki.extension.InstalledExtension;
import org.xwiki.extension.ResolveException;
import org.xwiki.extension.handler.ExtensionHandler;
import org.xwiki.extension.job.InstallRequest;
import org.xwiki.extension.job.plan.ExtensionPlanAction;
import org.xwiki.extension.job.plan.ExtensionPlanAction.Action;
import org.xwiki.extension.job.plan.ExtensionPlanNode;
import org.xwiki.extension.job.plan.internal.DefaultExtensionPlan;
import org.xwiki.extension.job.plan.internal.DefaultExtensionPlanAction;
import org.xwiki.extension.job.plan.internal.DefaultExtensionPlanNode;
import org.xwiki.extension.job.plan.internal.DefaultExtensionPlanTree;
import org.xwiki.extension.repository.CoreExtensionRepository;
import org.xwiki.extension.repository.ExtensionRepositoryManager;
import org.xwiki.extension.version.IncompatibleVersionConstraintException;
import org.xwiki.extension.version.VersionConstraint;

/**
 * Create an Extension plan.
 * 
 * @version $Id$
 * @since 4.1M1
 */
public abstract class AbstractInstallPlanJob<R extends InstallRequest> extends AbstractExtensionJob<R>
{
    protected static class ModifableExtensionPlanTree extends DefaultExtensionPlanTree implements Cloneable
    {
        private static final long serialVersionUID = 1L;

        @Override
        public ModifableExtensionPlanTree clone()
        {
            ModifableExtensionPlanTree tree = new ModifableExtensionPlanTree();

            for (ExtensionPlanNode node : this) {
                tree.add(((ModifableExtensionPlanNode) node).clone());
            }

            return tree;
        }
    }

    protected static class ModifableExtensionPlanNode extends DefaultExtensionPlanNode implements Cloneable
    {
        // never change

        private final ExtensionDependency initialDependency;

        // can change

        public VersionConstraint versionConstraint;

        public final List<ModifableExtensionPlanNode> duplicates = new ArrayList<ModifableExtensionPlanNode>();

        // helpers

        public ModifableExtensionPlanNode()
        {
            this.initialDependency = null;
        }

        public ModifableExtensionPlanNode(ExtensionDependency initialDependency, VersionConstraint versionConstraint)
        {
            this.initialDependency = initialDependency;
            this.versionConstraint = versionConstraint;
        }

        public ModifableExtensionPlanNode(ExtensionDependency initialDependency, ModifableExtensionPlanNode node)
        {
            this.initialDependency = initialDependency;

            set(node);
        }

        @Override
        protected ModifableExtensionPlanNode clone()
        {
            try {
                return (ModifableExtensionPlanNode) super.clone();
            } catch (CloneNotSupportedException e) {
                // this shouldn't happen, since we are Cloneable
                throw new InternalError();
            }
        }

        public void set(ModifableExtensionPlanNode node)
        {
            this.action = node.action;
            this.children = node.children;
        }

        @Override
        public VersionConstraint getInitialVersionConstraint()
        {
            return this.initialDependency.getVersionConstraint();
        }

        public void setAction(ExtensionPlanAction action)
        {
            this.action = action;
        }

        public void setChildren(Collection< ? extends ExtensionPlanNode> children)
        {
            this.children = (Collection) children;
        }
    }

    /**
     * Used to resolve extensions to install.
     */
    @Inject
    protected ExtensionRepositoryManager repositoryManager;

    /**
     * Used to check if extension or its dependencies are already core extensions.
     */
    @Inject
    protected CoreExtensionRepository coreExtensionRepository;

    /**
     * The install plan.
     */
    protected ModifableExtensionPlanTree extensionTree = new ModifableExtensionPlanTree();

    /**
     * Used to make sure dependencies are compatible between each other in the whole plan.
     * <p>
     * <id, <namespace, node>>.
     */
    private Map<String, Map<String, ModifableExtensionPlanNode>> extensionsNodeCache =
        new HashMap<String, Map<String, ModifableExtensionPlanNode>>();

    @Override
    protected DefaultExtensionPlan<R> createNewStatus(R request)
    {
        return new DefaultExtensionPlan<R>(request, this.observationManager, this.loggerManager, this.extensionTree);
    }

    protected void setExtensionTree(ModifableExtensionPlanTree extensionTree)
    {
        this.extensionTree = extensionTree;
        ((DefaultExtensionPlan<R>) this.status).setTree(this.extensionTree);
    }

    /**
     * @param extensionsByNamespace the map to fill
     * @param extensionId the id of the extension to install/upgrade
     * @param namespace the namespace where to install the extension
     */
    protected void addExtensionToProcess(Map<ExtensionId, Collection<String>> extensionsByNamespace,
        ExtensionId extensionId, String namespace)
    {
        Collection<String> namespaces;

        // Get namespaces
        if (extensionsByNamespace.containsKey(extensionId) && namespace != null) {
            namespaces = extensionsByNamespace.get(extensionId);
        } else {
            if (namespace == null) {
                namespaces = null;
            } else {
                namespaces = new HashSet<String>();
            }

            extensionsByNamespace.put(extensionId, namespaces);
        }

        // Add namespace
        if (namespaces != null) {
            namespaces.add(namespace);
        }
    }

    protected void start(Map<ExtensionId, Collection<String>> extensionsByNamespace) throws Exception
    {
        notifyPushLevelProgress(extensionsByNamespace.size());

        try {
            for (Map.Entry<ExtensionId, Collection<String>> entry : extensionsByNamespace.entrySet()) {
                ExtensionId extensionId = entry.getKey();
                Collection<String> namespaces = entry.getValue();

                if (namespaces != null) {
                    notifyPushLevelProgress(namespaces.size());

                    try {
                        for (String namespace : namespaces) {
                            installExtension(extensionId, namespace, this.extensionTree);

                            notifyStepPropress();
                        }
                    } finally {
                        notifyPopLevelProgress();
                    }
                } else {
                    installExtension(extensionId, null, this.extensionTree);
                }

                notifyStepPropress();
            }
        } finally {
            notifyPopLevelProgress();
        }
    }

    private ModifableExtensionPlanNode getExtensionNode(String id, String namespace)
    {
        Map<String, ModifableExtensionPlanNode> extensionsById = this.extensionsNodeCache.get(id);

        if (extensionsById != null) {
            ModifableExtensionPlanNode node = extensionsById.get(namespace);

            if (node == null && namespace != null) {
                node = extensionsById.get(null);
            }
        }

        return null;
    }

    private void addExtensionNode(ModifableExtensionPlanNode node)
    {
        String id = node.getAction().getExtension().getId().getId();

        Map<String, ModifableExtensionPlanNode> extensionsById = this.extensionsNodeCache.get(id);

        if (extensionsById == null) {
            extensionsById = new HashMap<String, ModifableExtensionPlanNode>();
            this.extensionsNodeCache.put(id, extensionsById);
        }

        ModifableExtensionPlanNode existingNode = extensionsById.get(node.getAction().getNamespace());

        if (existingNode != null) {
            existingNode.set(node);
            for (ModifableExtensionPlanNode duplicate : existingNode.duplicates) {
                duplicate.set(node);
            }
            existingNode.duplicates.add(node);
        } else {
            extensionsById.put(node.getAction().getNamespace(), node);
        }
    }

    /**
     * Install provided extension.
     * 
     * @param extensionId the identifier of the extension to install
     * @param namespace the namespace where to install the extension
     * @param parentBranch the children of the parent {@link DefaultExtensionPlanNode}
     * @throws InstallException error when trying to install provided extension
     */
    protected void installExtension(ExtensionId extensionId, String namespace, ModifableExtensionPlanTree parentBranch)
        throws InstallException
    {
        try {
            installExtension(extensionId, false, namespace, parentBranch);
        } catch (ResolveException e) {
            throw new InstallException("An unexpected exception has been raised", e);
        }
    }

    /**
     * Install provided extension.
     * 
     * @param extensionId the identifier of the extension to install
     * @param dependency indicate if the extension is installed as a dependency
     * @param namespace the namespace where to install the extension
     * @param parentBranch the children of the parent {@link DefaultExtensionPlanNode}
     * @throws InstallException error when trying to install provided extension
     * @throws ResolveException unexpected exception has been raised
     */
    protected void installExtension(ExtensionId extensionId, boolean dependency, String namespace,
        ModifableExtensionPlanTree parentBranch) throws InstallException, ResolveException
    {
        if (namespace != null) {
            this.logger.info("Resolving extension [{}] on namespace [{}]", extensionId, namespace);
        } else {
            this.logger.info("Resolving extension [{}] on all namespaces", extensionId);
        }

        // Make sure the extension is not already a core extension
        if (this.coreExtensionRepository.exists(extensionId.getId())) {
            throw new InstallException(String.format("There is already a core extension with the id [%s]",
                extensionId.getId()));
        }

        InstalledExtension previousExtension = null;

        InstalledExtension installedExtension =
            this.installedExtensionRepository.getInstalledExtension(extensionId.getId(), namespace);
        if (installedExtension != null) {
            this.logger.info("Found already installed extension with id [{}]. Checking compatibility.", extensionId);

            if (extensionId.getVersion() == null) {
                throw new InstallException(String.format("The extension with id [%s] is already installed",
                    extensionId.getId()));
            }

            int versionDiff = extensionId.getVersion().compareTo(installedExtension.getId().getVersion());

            if (versionDiff == 0) {
                throw new InstallException(String.format("The extension [%s] is already installed", extensionId));
            } else {
                // Change version
                previousExtension = installedExtension;

                // Make sure the new version is compatible with old version backward dependencies
                if (installedExtension.isInstalled(null)) {
                    Map<String, Collection<InstalledExtension>> backwardDependencies =
                        this.installedExtensionRepository.getBackwardDependencies(installedExtension.getId());

                    if (!isCompatible(backwardDependencies.get(null), extensionId)) {
                        throw new InstallException(String.format(
                            "The extension [%s] is not compatible with previous version ([%s]) backward dependencies",
                            extensionId, installedExtension.getId()));
                    }

                    if (namespace != null) {
                        if (!isCompatible(backwardDependencies.get(namespace), extensionId)) {
                            throw new InstallException(
                                String
                                    .format(
                                        "The extension [%s] is not compatible with previous version ([%s]) backward dependencies on namespace [%s]",
                                        extensionId, installedExtension.getId(), namespace));
                        }
                    }
                } else {
                    Collection<InstalledExtension> backwardDependencies =
                        this.installedExtensionRepository.getBackwardDependencies(installedExtension.getId().getId(),
                            namespace);

                    if (!isCompatible(backwardDependencies, extensionId)) {
                        throw new InstallException(
                            String
                                .format(
                                    "The extension [%s] is not compatible with previous version ([%s]) backward dependencies on namespace [%s]",
                                    extensionId, installedExtension.getId(), namespace));
                    }
                }
            }

            // upgrade
            previousExtension = installedExtension;
        }

        ModifableExtensionPlanNode node = installExtension(previousExtension, extensionId, dependency, namespace);

        addExtensionNode(node);
        parentBranch.add(node);
    }

    /**
     * Check if provided id/version is compatible with provided extensions dependencies constraints.
     */
    private boolean isCompatible(Collection< ? extends Extension> extensions, ExtensionId extensionId)
    {
        if (extensions != null) {
            for (Extension extension : extensions) {
                for (ExtensionDependency dependency : extension.getDependencies()) {
                    if (dependency.getId().equals(extensionId.getId())) {
                        if (!dependency.getVersionConstraint().isCompatible(extensionId.getVersion())) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    private boolean checkCoreExtension(ExtensionDependency extensionDependency,
        List<ModifableExtensionPlanNode> parentBranch) throws InstallException
    {
        CoreExtension coreExtension = this.coreExtensionRepository.getCoreExtension(extensionDependency.getId());

        if (coreExtension != null) {
            if (!extensionDependency.getVersionConstraint().isCompatible(coreExtension.getId().getVersion())) {
                throw new InstallException("Dependency [" + extensionDependency
                    + "] is not compatible with core extension [" + coreExtension + "]");
            } else {
                this.logger.info("There is already a core extension [{}] covering extension dependency [{}]",
                    coreExtension.getId(), extensionDependency);

                ModifableExtensionPlanNode node =
                    new ModifableExtensionPlanNode(extensionDependency, extensionDependency.getVersionConstraint());
                node.setAction(new DefaultExtensionPlanAction(coreExtension, null, Action.NONE, null, true));

                parentBranch.add(node);

                return true;
            }
        }

        return false;
    }

    private VersionConstraint checkExistingPlanNode(ExtensionDependency extensionDependency, String namespace,
        List<ModifableExtensionPlanNode> parentBranch, VersionConstraint previousVersionConstraint)
        throws InstallException
    {
        VersionConstraint versionConstraint = previousVersionConstraint;

        ModifableExtensionPlanNode existingNode = getExtensionNode(extensionDependency.getId(), namespace);
        if (existingNode != null) {
            if (versionConstraint.isCompatible(existingNode.getAction().getExtension().getId().getVersion())) {
                ModifableExtensionPlanNode node = new ModifableExtensionPlanNode(extensionDependency, existingNode);
                addExtensionNode(node);
                parentBranch.add(node);

                return null;
            } else {
                if (existingNode.versionConstraint != null) {
                    try {
                        versionConstraint = versionConstraint.merge(existingNode.versionConstraint);
                    } catch (IncompatibleVersionConstraintException e) {
                        throw new InstallException("Dependency [" + extensionDependency
                            + "] is incompatible with current containt [" + existingNode.versionConstraint + "]", e);
                    }
                } else {
                    throw new InstallException("Dependency [" + extensionDependency + "] incompatible with extension ["
                        + existingNode.getAction().getExtension() + "]");
                }
            }
        }

        return versionConstraint;
    }

    private ExtensionDependency checkInstalledExtension(InstalledExtension installedExtension,
        ExtensionDependency extensionDependency, VersionConstraint versionConstraint, String namespace,
        List<ModifableExtensionPlanNode> parentBranch) throws InstallException
    {
        ExtensionDependency targetDependency = extensionDependency;

        if (installedExtension != null) {
            // Check if already installed version is compatible
            if (installedExtension.isValid(namespace)
                && versionConstraint.isCompatible(installedExtension.getId().getVersion())) {
                this.logger.info("There is already an installed extension [{}] covering extension dependency [{}]",
                    installedExtension.getId(), extensionDependency);

                ModifableExtensionPlanNode node =
                    new ModifableExtensionPlanNode(extensionDependency, versionConstraint);
                node.setAction(new DefaultExtensionPlanAction(installedExtension, null, Action.NONE, namespace,
                    installedExtension.isDependency()));

                addExtensionNode(node);
                parentBranch.add(node);

                return null;
            }

            // If not compatible with it, try to merge dependencies constraint of all backward dependencies to find a
            // new compatible version for this extension
            VersionConstraint mergedVersionContraint;
            try {
                if (installedExtension.isInstalled(null)) {
                    Map<String, Collection<InstalledExtension>> backwardDependencies =
                        this.installedExtensionRepository.getBackwardDependencies(installedExtension.getId());

                    mergedVersionContraint =
                        mergeVersionConstraints(backwardDependencies.get(null), extensionDependency.getId(),
                            versionConstraint);
                    if (namespace != null) {
                        mergedVersionContraint =
                            mergeVersionConstraints(backwardDependencies.get(namespace), extensionDependency.getId(),
                                mergedVersionContraint);
                    }
                } else {
                    Collection<InstalledExtension> backwardDependencies =
                        this.installedExtensionRepository.getBackwardDependencies(installedExtension.getId().getId(),
                            namespace);

                    mergedVersionContraint =
                        mergeVersionConstraints(backwardDependencies, extensionDependency.getId(), versionConstraint);
                }
            } catch (IncompatibleVersionConstraintException e) {
                throw new InstallException("Provided depency is incompatible with already installed extensions", e);
            } catch (ResolveException e) {
                throw new InstallException("Failed to resolve backward dependencies", e);
            }

            if (mergedVersionContraint != versionConstraint) {
                targetDependency = new DefaultExtensionDependency(extensionDependency, mergedVersionContraint);
            }
        }

        return targetDependency;
    }

    /**
     * Install provided extension dependency.
     * 
     * @param extensionDependency the extension dependency to install
     * @param namespace the namespace where to install the extension
     * @param parentBranch the children of the parent {@link DefaultExtensionPlanNode}
     * @throws InstallException error when trying to install provided extension
     */
    private void installExtensionDependency(ExtensionDependency extensionDependency, String namespace,
        List<ModifableExtensionPlanNode> parentBranch) throws InstallException
    {
        if (namespace != null) {
            this.logger.info("Resolving extension dependency [{}] on namespace [{}]", extensionDependency, namespace);
        } else {
            this.logger.info("Resolving extension dependency [{}] on all namespaces", extensionDependency);
        }

        VersionConstraint versionConstraint = extensionDependency.getVersionConstraint();

        // Make sure the dependency is not already a core extension
        if (checkCoreExtension(extensionDependency, parentBranch)) {
            // Already exists and added to the tree by #checkCoreExtension
            return;
        }

        // Make sure the dependency is not already in the current plan
        versionConstraint = checkExistingPlanNode(extensionDependency, namespace, parentBranch, versionConstraint);
        if (versionConstraint == null) {
            // Already exists and added to the tree by #checkExistingPlan
            return;
        }

        // Check installed extensions
        InstalledExtension previousExtension =
            this.installedExtensionRepository.getInstalledExtension(extensionDependency.getId(), namespace);
        ExtensionDependency targetDependency =
            checkInstalledExtension(previousExtension, extensionDependency, versionConstraint, namespace, parentBranch);
        if (targetDependency == null) {
            // Already exists and added to the tree by #checkInstalledExtension
            return;
        }

        // Not found locally, search it remotely
        ModifableExtensionPlanNode node = installExtension(previousExtension, targetDependency, true, namespace);

        node.versionConstraint = versionConstraint;

        addExtensionNode(node);
        parentBranch.add(node);
    }

    /**
     * Install provided extension.
     * 
     * @param previousExtension the previous installed version of the extension to install
     * @param targetDependency used to search the extension to install in remote repositories
     * @param dependency indicate if the extension is installed as a dependency
     * @param namespace the namespace where to install the extension
     * @return the install plan node for the provided extension
     * @throws InstallException error when trying to install provided extension
     */
    private ModifableExtensionPlanNode installExtension(InstalledExtension previousExtension,
        ExtensionDependency targetDependency, boolean dependency, String namespace) throws InstallException
    {
        notifyPushLevelProgress(2);

        try {
            // Check if the extension is already in local repository
            Extension extension = resolveExtension(targetDependency);

            notifyStepPropress();

            try {
                return installExtension(previousExtension, extension, dependency, namespace, targetDependency);
            } catch (Exception e) {
                throw new InstallException("Failed to resolve extension dependency", e);
            }
        } finally {
            notifyPopLevelProgress();
        }
    }

    /**
     * @param extensions the extensions containing the dependencies for which to merge the constraints
     * @param dependencyId the id of the dependency
     * @param previousMergedVersionContraint if not null it's merged with the provided extension dependencies version
     *            constraints
     * @return the merged version constraint
     * @throws IncompatibleVersionConstraintException the provided version constraint is compatible with the provided
     *             version constraint
     */
    private VersionConstraint mergeVersionConstraints(Collection< ? extends Extension> extensions, String dependencyId,
        VersionConstraint previousMergedVersionContraint) throws IncompatibleVersionConstraintException
    {
        VersionConstraint mergedVersionContraint = previousMergedVersionContraint;

        if (extensions != null) {
            for (Extension extension : extensions) {
                ExtensionDependency dependency = getDependency(extension, dependencyId);

                if (dependency != null) {
                    if (mergedVersionContraint == null) {
                        mergedVersionContraint = dependency.getVersionConstraint();
                    } else {
                        mergedVersionContraint = mergedVersionContraint.merge(dependency.getVersionConstraint());
                    }
                }
            }
        }

        return mergedVersionContraint;
    }

    /**
     * Extract extension with the provided id from the provided extension.
     * 
     * @param extension the extension
     * @param dependencyId the id of the dependency
     * @return the extension dependency or null if none has been found
     */
    private ExtensionDependency getDependency(Extension extension, String dependencyId)
    {
        for (ExtensionDependency dependency : extension.getDependencies()) {
            if (dependency.getId().equals(dependencyId)) {
                return dependency;
            }
        }

        return null;
    }

    /**
     * Install provided extension.
     * 
     * @param previousExtension the previous installed version of the extension to install
     * @param extensionId the identifier of the extension to install
     * @param dependency indicate if the extension is installed as a dependency
     * @param namespace the namespace where to install the extension
     * @return the install plan node for the provided extension
     * @throws InstallException error when trying to install provided extension
     */
    private ModifableExtensionPlanNode installExtension(InstalledExtension previousExtension, ExtensionId extensionId,
        boolean dependency, String namespace) throws InstallException
    {
        notifyPushLevelProgress(2);

        try {
            // Check is the extension is already in local repository
            Extension extension = resolveExtension(extensionId, dependency);

            notifyStepPropress();

            try {
                return installExtension(previousExtension, extension, dependency, namespace, null);
            } catch (Exception e) {
                throw new InstallException("Failed to resolve extension", e);
            }
        } finally {
            notifyPopLevelProgress();
        }
    }

    /**
     * @param extensionId the identifier of the extension to install
     * @return the extension
     * @throws InstallException error when trying to resolve extension
     */
    private Extension resolveExtension(ExtensionId extensionId, boolean dependency) throws InstallException
    {
        Extension extension = null;

        if (dependency || !getRequest().isIgnoreLocal()) {
            // Check is the extension is already in local repository
            try {
                extension = this.localExtensionRepository.resolve(extensionId);
            } catch (ResolveException e) {
                this.logger.debug("Can't find extension in local repository, trying to download it.", e);
            }
        }

        if (extension == null) {
            // Resolve extension
            try {
                extension = this.repositoryManager.resolve(extensionId);
            } catch (ResolveException e1) {
                throw new InstallException(String.format("Failed to resolve extension [%s]", extensionId), e1);
            }
        }

        return extension;
    }

    /**
     * @param extensionDependency describe the extension to install
     * @return the extension
     * @throws InstallException error when trying to resolve extension
     */
    private Extension resolveExtension(ExtensionDependency extensionDependency) throws InstallException
    {
        // Check is the extension is already in local repository
        Extension extension;
        try {
            extension = this.localExtensionRepository.resolve(extensionDependency);
        } catch (ResolveException e) {
            this.logger.debug("Can't find extension dependency in local repository, trying to download it.", e);

            // Resolve extension
            try {
                extension = this.repositoryManager.resolve(extensionDependency);
            } catch (ResolveException e1) {
                throw new InstallException(String.format("Failed to resolve extension dependency [%s]",
                    extensionDependency), e1);
            }
        }

        return extension;
    }

    /**
     * @param previousExtension the previous installed version of the extension to install
     * @param extension the new extension to install
     * @param dependency indicate if the extension is installed as a dependency
     * @param namespace the namespace where to install the extension
     * @return the install plan node for the provided extension
     * @param initialDependency the initial dependency used to resolve the extension
     * @throws InstallException error when trying to install provided extension
     */
    private ModifableExtensionPlanNode installExtension(InstalledExtension previousExtension, Extension extension,
        boolean dependency, String namespace, ExtensionDependency initialDependency) throws InstallException
    {
        try {
            this.componentManager.getInstance(ExtensionHandler.class, extension.getType());
        } catch (ComponentLookupException e) {
            throw new InstallException(String.format("Unsupported type [%s]", extension.getType()), e);
        }

        Collection< ? extends ExtensionDependency> dependencies = extension.getDependencies();

        notifyPushLevelProgress(dependencies.size() + 1);

        try {
            List<ModifableExtensionPlanNode> children = null;
            if (!dependencies.isEmpty()) {
                children = new ArrayList<ModifableExtensionPlanNode>();
                for (ExtensionDependency dependencyDependency : extension.getDependencies()) {
                    installExtensionDependency(dependencyDependency, namespace, children);

                    notifyStepPropress();
                }
            }

            ModifableExtensionPlanNode node =
                initialDependency != null ? new ModifableExtensionPlanNode(initialDependency,
                    initialDependency.getVersionConstraint()) : new ModifableExtensionPlanNode();

            node.setChildren(children);

            Action action;
            if (previousExtension != null) {
                if (previousExtension.getId().getVersion().compareTo(extension.getId().getVersion()) > 0) {
                    action = Action.DOWNGRADE;
                } else {
                    action = Action.UPGRADE;
                }
            } else {
                action = Action.INSTALL;
            }

            node.setAction(new DefaultExtensionPlanAction(extension, previousExtension, action, namespace, dependency));

            return node;
        } finally {
            notifyPopLevelProgress();
        }
    }
}
