/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *      Vladimir Pasquier <vpasquier@nuxeo.com>
 */
package org.nuxeo.binary.metadata.test;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.automation.core.util.DocumentHelper;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;

/**
 * @since 7.1
 */
@RunWith(FeaturesRunner.class)
@Features(BinaryMetadataFeature.class)
@Deploy({ "org.nuxeo.ecm.platform.picture.api", "org.nuxeo.ecm.platform.picture.core" })
@LocalDeploy({ "org.nuxeo.binary.metadata:binary-metadata-contrib-test.xml",
        "org.nuxeo.binary.metadata:binary-metadata-contrib-pdf-test.xml" })
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestBinaryMetadataSyncListener {

    @Inject
    CoreSession session;

    @Test
    public void testListener() throws Exception {
        // Create folder
        DocumentModel doc = session.createDocumentModel("/", "folder", "Folder");
        doc.setPropertyValue("dc:title", "Folder");
        session.createDocument(doc);

        // Create file
        doc = session.createDocumentModel("/folder", "file", "File");
        doc.setPropertyValue("dc:title", "file");
        doc = session.createDocument(doc);

        // Attach PDF
        File binary = FileUtils.getResourceFileFromContext("data/hello.pdf");
        Blob fb = Blobs.createBlob(binary, "application/pdf");
        DocumentHelper.addBlob(doc.getProperty("file:content"), fb);
        session.saveDocument(doc);

        DocumentModel pdfDoc = session.getDocument(doc.getRef());

        assertEquals("en-US", pdfDoc.getPropertyValue("dc:title"));
        assertEquals("OpenOffice.org 3.2", pdfDoc.getPropertyValue("dc:source"));
        assertEquals("Writer", pdfDoc.getPropertyValue("dc:coverage"));
        assertEquals("Mirko Nasato", pdfDoc.getPropertyValue("dc:creator"));

        // Test if description has been overriden by higher order contribution
        assertEquals("OpenOffice.org 3.2", pdfDoc.getPropertyValue("dc:description"));

        // Test the following rule: 'If the attached binary is dirty and the document metadata are not dirty, the
        // listener reads the metadata from attached binary to document.'

        // Changing the title to see after if the blob title is well propagated.
        pdfDoc.setPropertyValue("dc:title", "notFromBlob");
        pdfDoc.setPropertyValue("file:content", null);
        session.saveDocument(pdfDoc);

        pdfDoc = session.getDocument(pdfDoc.getRef());

        assertEquals("notFromBlob", pdfDoc.getPropertyValue("dc:title"));

        // Updating only the blob and simulate the same change on dc:title -> title should not be dirty.
        pdfDoc.setPropertyValue("dc:title", "notFromBlob");
        DocumentHelper.addBlob(pdfDoc.getProperty("file:content"), fb);
        session.saveDocument(pdfDoc);

        pdfDoc = session.getDocument(pdfDoc.getRef());

        // Confirm the blob was dirty but not metadata -> title should be updated properly.
        assertEquals("en-US", pdfDoc.getPropertyValue("dc:title"));
    }

    @Test
    public void testEXIFandIPTC() throws IOException {
        // Create folder
        DocumentModel doc = session.createDocumentModel("/", "folder", "Folder");
        doc.setPropertyValue("dc:title", "Folder");
        session.createDocument(doc);

        // Create first picture
        doc = session.createDocumentModel("/folder", "picture", "Picture");
        doc.setPropertyValue("dc:title", "picture");
        doc = session.createDocument(doc);

        // Attach EXIF sample
        File binary = FileUtils.getResourceFileFromContext("data/china.jpg");
        Blob fb = Blobs.createBlob(binary, "image/jpeg");
        DocumentHelper.addBlob(doc.getProperty("file:content"), fb);
        session.saveDocument(doc);

        // Verify
        DocumentModel picture = session.getDocument(doc.getRef());
        assertEquals("Horizontal (normal)", picture.getPropertyValue("imd:orientation"));
        assertEquals(2.4, picture.getPropertyValue("imd:fnumber"));

        // Create second picture
        doc = session.createDocumentModel("/folder", "picture1", "Picture");
        doc.setPropertyValue("dc:title", "picture");
        doc = session.createDocument(doc);

        // Attach IPTC sample
        binary = FileUtils.getResourceFileFromContext("data/iptc_sample.jpg");
        fb = Blobs.createBlob(binary, "image/jpeg");
        DocumentHelper.addBlob(doc.getProperty("file:content"), fb);
        session.saveDocument(doc);

        // Verify
        picture = session.getDocument(doc.getRef());
        assertEquals("DDP", picture.getPropertyValue("dc:source"));
        assertEquals("ImageForum", picture.getPropertyValue("dc:rights"));
    }
}
