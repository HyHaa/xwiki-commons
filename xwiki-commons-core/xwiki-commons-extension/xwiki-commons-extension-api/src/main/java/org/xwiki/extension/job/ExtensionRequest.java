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
package org.xwiki.extension.job;

import java.util.Collection;

import org.xwiki.extension.ExtensionId;
import org.xwiki.job.Request;

/**
 * Extension manipulation related {@link Request}.
 * 
 * @version $Id$
 * @since 4.0M1
 */
public interface ExtensionRequest extends Request
{
    /**
     * @see #getExtensions()
     * @since 4.1M2
     */
    String PROPERTY_EXTENSIONS = "extensions";

    /**
     * @see #getNamespaces()
     * @since 4.1M2
     */
    String PROPERTY_NAMESPACES = "namespaces";

    /**
     * @see #isCleanLocal()
     * @since 4.1M2
     */
    String PROPERTY_CLEANLOCAL = "cleanlocal";

    /**
     * @return the extension on which to apply the task.
     */
    Collection<ExtensionId> getExtensions();

    /**
     * @return the namespaces on which to apply the task.
     */
    Collection<String> getNamespaces();

    /**
     * @return indicate if the request is applied on specific namespace or all of them
     */
    boolean hasNamespaces();

    /**
     * @return indicate of the local repository should be cleaned when an extension is removed
     */
    boolean isCleanLocal();
}
