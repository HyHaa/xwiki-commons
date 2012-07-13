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
package org.xwiki.tool.xar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.OutputStream;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.archiver.ArchiveEntry;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.dom.DOMDocument;
import org.dom4j.dom.DOMElement;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

/**
 * Gather all resources in a XAR file (which is actually a ZIP file). Also generates a XAR descriptor if none is
 * provided.
 * 
 * @version $Id$
 * @goal xar
 * @phase package
 * @requiresProject
 * @requiresDependencyResolution compile
 * @threadSafe
 */
public class XARMojo extends AbstractXARMojo
{
    /**
     * Indicate if XAR dependencies should be included in the produced XAR package.
     * 
     * @parameter expression="${includeDependencies}" default-value="false"
     */
    private boolean includeDependencies;

    @Override
    public void execute() throws MojoExecutionException
    {
        if (this.project.getResources().size() < 1) {
            getLog().warn("No XAR created as no resources were found");
            return;
        }

        try {
            performArchive();
        } catch (Exception e) {
            throw new MojoExecutionException("Error while creating XAR file", e);
        }
    }

    /**
     * Create the XAR by zipping the resource files.
     * 
     * @throws Exception if the zipping failed for some reason
     */
    private void performArchive() throws Exception
    {
        File xarFile = new File(this.project.getBuild().getDirectory(), this.project.getArtifactId() + ".xar");
        
        // The source dir points to the target/classes directory where the Maven resources plugin
        // has copied the XAR files during the process-resources phase.
        // For package.xml, however, we look in the resources directory (i.e. src/main/resources).
        File sourceDir = new File(this.project.getBuild().getOutputDirectory());
        File resourcesDir = getResourcesDirectory();

        // Check that there are files in the source dir
        if (sourceDir.listFiles() == null) {
            throw new Exception(
                String.format("No XAR XML files found in [%s]. Has the Maven Resource plugin be called?", sourceDir));
        }

        ZipArchiver archiver = new ZipArchiver();
        archiver.setEncoding(this.encoding);
        archiver.setDestFile(xarFile);
        archiver.setIncludeEmptyDirs(false);
        archiver.setCompress(true);

        if (this.includeDependencies) {
            // Unzip dependent XARs on top of this project's XML documents but without overwriting
            // existing files since we want this projet's files to be used if they override a file
            // present in a XAR dependency.
            unpackDependentXARs();
        }

        // If no package.xml can be found at the top level of the current project, generate one
        // otherwise, try to use the existing one
        FilenameFilter packageXmlFiler = new FilenameFilter()
        {
            @Override
            public boolean accept(File dir, String name)
            {
                return (name.equals(PACKAGE_XML));
            }
        };
        if (!resourcesDir.exists() || resourcesDir.list(packageXmlFiler).length == 0) {
            addFilesToArchive(archiver, sourceDir);
        } else {
            File packageXml = new File(resourcesDir, PACKAGE_XML);
            addFilesToArchive(archiver, sourceDir, packageXml);
        }

        archiver.createArchive();

        this.project.getArtifact().setFile(xarFile);
    }

    /**
     * Unpack XAR dependencies before pack then into it.
     * 
     * @throws MojoExecutionException error when unpack dependencies.
     */
    private void unpackDependentXARs() throws MojoExecutionException
    {
        Set<Artifact> artifacts = this.project.getArtifacts();
        if (artifacts != null) {
            for (Artifact artifact : artifacts) {
                if (!artifact.isOptional()) {
                    if ("xar".equals(artifact.getType())) {
                        unpackXARToOutputDirectory(artifact);
                    }
                }
            }
        }
    }
    
    /**
     * Create and add package configuration file to the package.
     * 
     * @param packageFile the package when to add configuration file.
     * @param files the files in the package.
     * @throws Exception error when writing the configuration file.
     */
    private void generatePackageXml(File packageFile, Collection<ArchiveEntry> files) throws Exception
    {
        getLog().info(String.format("Generating package.xml descriptor at [%s]", packageFile.getPath()));

        OutputFormat outputFormat = new OutputFormat("", true);
        outputFormat.setEncoding(this.encoding);
        OutputStream out = new FileOutputStream(packageFile);
        XMLWriter writer = new XMLWriter(out, outputFormat);
        writer.write(toXML(files));
        writer.close();
        out.close();
    }

    /**
     * Generate a DOM4J Document containing the generated XML.
     * 
     * @param files the list of files that we want to include in the generated package XML file.
     * @return the DOM4J Document containing the generated XML
     */
    private Document toXML(Collection<ArchiveEntry> files)
    {
        Document doc = new DOMDocument();

        Element packageElement = new DOMElement("package");
        doc.setRootElement(packageElement);

        Element infoElement = new DOMElement("infos");
        packageElement.add(infoElement);
        addInfoElements(infoElement);

        Element filesElement = new DOMElement(FILES_TAG);
        packageElement.add(filesElement);
        addFileElements(files, filesElement);

        return doc;
    }

    /**
     * Add all the XML elements under the &lt;info&gt; element (name, description, license, author, version and whether
     * it's a backup pack or not).
     * 
     * @param infoElement the info element to which to add to
     */
    private void addInfoElements(Element infoElement)
    {
        Element el = new DOMElement("name");
        el.addText(this.project.getName());
        infoElement.add(el);

        el = new DOMElement("description");
        String description = this.project.getDescription();
        if (description == null) {
            el.addText("");
        } else {
            el.addText(description);
        }
        infoElement.add(el);

        el = new DOMElement("licence");
        el.addText("");
        infoElement.add(el);

        el = new DOMElement("author");
        el.addText("XWiki.Admin");
        infoElement.add(el);

        el = new DOMElement("version");
        el.addText(this.project.getVersion());
        infoElement.add(el);

        el = new DOMElement("backupPack");
        el.addText("true");
        infoElement.add(el);
    }

    /**
     * Add all the XML elements under the &lt;files&gt; element (the list of files present in the XAR).
     * 
     * @param files the list of files that we want to include in the generated package XML file.
     * @param filesElement the files element to which to add to
     */
    private void addFileElements(Collection<ArchiveEntry> files, Element filesElement)
    {
        for (ArchiveEntry entry : files) {
            // Don't add files in META-INF to the package.xml file
            if (entry.getName().indexOf("META-INF") == -1) {
                XWikiDocument xdoc = getDocFromXML(entry.getFile());
                if (xdoc != null) {
                    String fullName = xdoc.getFullName();
                    Element element = new DOMElement(FILE_TAG);
                    element.setText(fullName);
                    element.addAttribute("language", xdoc.getLanguage());
                    element.addAttribute("defaultAction", "0");
                    filesElement.add(element);
                }
            }
        }
    }

    /**
     * Gets the list of document names from a 'package.xml'-like document.
     * 
     * @param file the XML document to parse
     * @return the list of document names contained in the XML document
     * @throws Exception if the XML document is invalid or it contains no document list or it doesn't exist
     */
    protected static Collection<String> getDocumentNamesFromXML(File file) throws Exception
    {
        Collection<String> result = new LinkedList<String>();
        SAXReader reader = new SAXReader();
        Document domdoc;
        domdoc = reader.read(file);

        Element filesElement = domdoc.getRootElement().element(FILES_TAG);

        if (filesElement == null) {
            throw new Exception("The supplied document contains no document list ");
        }

        Collection elements = filesElement.elements(FILE_TAG);
        for (Object item : elements) {
            if (item instanceof Element) {
                Element currentElement = (Element) item;
                String documentName = currentElement.getText();
                result.add(documentName);
            }
        }

        return result;
    }

    /**
     * Adds the files from a specific directory to an archive. It also builds a package.xml file based on that content
     * which is also added to the archive.
     * 
     * @param archiver the archive in which the files will be added
     * @param sourceDir the directory whose contents will be added to the archive
     * @throws Exception if the files cannot be added to the archive
     */
    private void addFilesToArchive(ZipArchiver archiver, File sourceDir) throws Exception
    {
        File generatedPackageFile = new File(sourceDir, PACKAGE_XML);
        if (generatedPackageFile.exists()) {
            generatedPackageFile.delete();
        }

        archiver.addDirectory(sourceDir, getIncludes(), getExcludes());
        generatePackageXml(generatedPackageFile, archiver.getFiles().values());
        archiver.addFile(generatedPackageFile, PACKAGE_XML);
    }

    /**
     * Adds files from a specific directory to an archive. It uses an existing package.xml to filter the files to be
     * added.
     * 
     * @param archiver the archive in which the files will be added
     * @param sourceDir the directory whose contents will be added to the archive
     * @param packageXml the corresponding package.xml file
     * @throws Exception if the files cannot be added to the archive
     */
    private void addFilesToArchive(ZipArchiver archiver, File sourceDir, File packageXml) throws Exception
    {
        Collection<String> documentNames;
        getLog().info(String.format("Using the existing package.xml descriptor at [%s]", packageXml.getPath()));
        try {
            documentNames = getDocumentNamesFromXML(packageXml);
        } catch (Exception e) {
            getLog().error(String.format("The existing [%s] is invalid.", PACKAGE_XML));
            throw e;
        }

        // Next, we scan the hole directory and subdirectories for documents.

        Queue<File> fileQueue = new LinkedList<File>();
        addContentsToQueue(fileQueue, sourceDir);
        while (!fileQueue.isEmpty() && !documentNames.isEmpty()) {
            File currentFile = fileQueue.poll();
            if (currentFile.isDirectory()) {
                addContentsToQueue(fileQueue, currentFile);
            } else {
                String documentName = XWikiDocument.getFullName(currentFile);
                if (documentNames.contains(documentName)) {

                    // building the path the current file will have within the archive
                    /*
                     * DO NOT USE String.split since it requires a regexp. Under Windows XP, the FileSeparator is '\'
                     * when not escaped is a special character of the regexp String archivedFilePath =
                     * currentFile.getAbsolutePath().split(sourceDir.getAbsolutePath() + File.separator)[1];
                     */
                    String archivedFilePath =
                        currentFile.getAbsolutePath()
                            .substring((sourceDir.getAbsolutePath() + File.separator).length());
                    archivedFilePath.replace(File.separatorChar, '/');

                    archiver.addFile(currentFile, archivedFilePath);
                    documentNames.remove(documentName);
                }
            }
        }

        if (!documentNames.isEmpty()) {
            StringBuilder errorMessage = new StringBuilder("The following documents could not be found: ");
            for (String name : documentNames) {
                errorMessage.append(name);
                errorMessage.append(" ");
            }
            throw new Exception(errorMessage.toString());
        }

        archiver.addFile(packageXml, PACKAGE_XML);
    }

    /**
     * Adds the contents of a specific directory to a queue of files.
     * 
     * @param fileQueue the queue of files
     * @param sourceDir the directory to be scanned
     */
    private static void addContentsToQueue(Queue<File> fileQueue, File sourceDir)
    {
        for (File currentFile : sourceDir.listFiles()) {
            fileQueue.add(currentFile);
        }
    }
}
