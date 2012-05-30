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
package org.xwiki.environment.internal;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import javax.inject.Singleton;
import javax.servlet.ServletContext;

import org.xwiki.component.annotation.Component;

/**
 * Defines what an Environment means in a Servlet environment.
 *
 * @version $Id$
 * @since 3.5M1
 */
@Component
@Singleton
public class ServletEnvironment extends AbstractEnvironment
{
    /**
     * @see #getServletContext()
     */
    private ServletContext servletContext;

    /**
     * @param servletContext see {@link #getServletContext()}
     */
    public void setServletContext(ServletContext servletContext)
    {
        this.servletContext = servletContext;
    }

    /**
     * @return the Servlet Context
     */
    public ServletContext getServletContext()
    {
        if (this.servletContext == null) {
            throw new RuntimeException("The Servlet Environment has not been properly initialized "
                + "(The Servlet Context is not set)");
        }
        return this.servletContext;
    }

    @Override
    public InputStream getResourceAsStream(String resourceName)
    {
        return getServletContext().getResourceAsStream(resourceName);
    }

    @Override
    public URL getResource(String resourceName)
    {
        try {
            return getServletContext().getResource(resourceName);
        } catch (MalformedURLException e) {
            throw new RuntimeException(String.format("Failed to access resource [%s]", resourceName), e);
        }
    }

    @Override
    protected String getTemporaryDirectoryName()
    {
        final String tmpDirectory = super.getTemporaryDirectoryName();
        try {
            if (tmpDirectory == null) {
                return ((File) this.getServletContext().getAttribute("javax.servlet.context.tempdir"))
                    .getCanonicalPath();
            }
        } catch (IOException e) {
            this.logger.warn("Unable to get servlet temporary directory due to error [{}], "
                             + "falling back on default temp directory.", e.getMessage());
        }
        return tmpDirectory;
    }
}
