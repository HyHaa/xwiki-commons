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

import org.xwiki.job.Request;

/**
 * Request used in {@link org.xwiki.extension.job.internal.InstallJob}.
 * 
 * @version $Id$
 * @since 4.0M1
 */
public class InstallRequest extends AbstractExtensionRequest
{
    /**
     * @see #isIgnoreLocal()
     * @since 4.1M2
     */
    public static final String PROPERTY_IGNORELOCAL = "ignorelocal";

    /**
     * @see #isReinstall()
     * @since 4.1M2
     */
    public static final String PROPERTY_REINSTALL = "reinstall";

    /**
     * Serialization identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Default constructor.
     */
    public InstallRequest()
    {
    }

    /**
     * @param request the request to copy
     */
    public InstallRequest(Request request)
    {
        super(request);
    }

    /**
     * @param ignoreLocal true if local repository should not be taken into account when resolving the extension
     * @since 4.1M2
     */
    public void setIgnoreLocal(boolean ignoreLocal)
    {
        setProperty(PROPERTY_IGNORELOCAL, ignoreLocal);
    }

    /**
     * @return true if local repository should not be taken into account when resolving the extension
     * @since 4.1M2
     */
    public boolean isIgnoreLocal()
    {
        return getProperty(PROPERTY_IGNORELOCAL, false);
    }

    /**
     * @param reinstall true if the listed extension should be resinstalled if already installed
     * @since 4.1M2
     */
    public void setReinstall(boolean reinstall)
    {
        setProperty(PROPERTY_IGNORELOCAL, reinstall);
    }

    /**
     * @return true if the listed extension should be resinstalled if already installed
     * @since 4.1M2
     */
    public boolean isReinstall()
    {
        return getProperty(PROPERTY_IGNORELOCAL, false);
    }

}
