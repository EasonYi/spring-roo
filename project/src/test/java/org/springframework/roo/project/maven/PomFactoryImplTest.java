package org.springframework.roo.project.maven;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.Iterator;

import org.junit.Before;
import org.junit.Test;
import org.springframework.roo.project.Dependency;
import org.springframework.roo.support.util.Pair;
import org.springframework.uaa.client.util.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Unit test of {@link PomFactoryImpl}
 *
 * @author Andrew Swan
 * @since 1.2.0
 */
public class PomFactoryImplTest {
	
	// Constants
	private static final String MODULE_NAME = "my-module";
	
	// Fixture
	private PomFactory factory;

	@Before
	public void setUp() throws Exception {
		this.factory = new PomFactoryImpl();
	}
	
	/**
	 * Returns the URL of the given POM file
	 * 
	 * @param pomFileName the name of a POM in this test's package
	 * @return a non-<code>null</code> URL
	 * @throws Exception
	 */
	private URL getPomUrl(final String pomFileName) throws Exception {
		final URL pomUrl = getClass().getResource(pomFileName);
		assertNotNull("Can't find test POM '" + pomFileName + "' on classpath of " + getClass().getName(), pomUrl);
		return pomUrl;
	}
	
	/**
	 * Returns the root element and canonical path of the given POM file
	 * 
	 * @param pomFileName the name of a POM in this test's package
	 * @return a non-<code>null</code> pair
	 * @throws Exception
	 */
	private Pair<Element, String> getPom(final String pomFileName) throws Exception {
		final URL pomUrl = getPomUrl(pomFileName);
		final File pomFile = new File(pomUrl.toURI());
		final Document pomDocument = XmlUtils.parse(pomUrl.openStream());
		return new Pair<Element, String>(pomDocument.getDocumentElement(), pomFile.getCanonicalPath());
	}
	
	private File getPomFile(final String pomFileName) throws Exception {
		final URL pomUrl = getPomUrl(pomFileName);
		return new File(pomUrl.toURI());
	}
	
	private Pom invokeFactory(final String pomFile) throws Exception {
		final Pair<Element, String> pomDetails = getPom(pomFile);
		return factory.getInstance(pomDetails.getKey(), pomDetails.getValue(), MODULE_NAME);
	}
	
	private void assertGav(final Pom pom, final String expectedGroupId, final String expectedArtifactId, final String expectedVersion) {
		assertEquals(expectedGroupId, pom.getGroupId());
		assertEquals(expectedArtifactId, pom.getArtifactId());
		assertEquals(expectedVersion, pom.getVersion());
	}
	
	@Test
	public void testGetMinimalInstance() throws Exception {
		// Invoke
		final Pom pom = invokeFactory("minimal-pom.xml");
		
		// Check
		assertGav(pom, "com.example", "minimal-app", "2.0");
		assertEquals(Pom.DEFAULT_SOURCE_DIRECTORY, pom.getSourceDirectory());
		assertEquals(Pom.DEFAULT_TEST_SOURCE_DIRECTORY, pom.getTestSourceDirectory());
	}
	
	@Test
	public void testGetInstanceWithDependency() throws Exception {
		// Invoke
		final Pom pom = invokeFactory("pom-with-dependencies.xml");
		
		// Check
		assertGav(pom, "com.example", "dependent-app", "2.1");
		final Collection<Dependency> dependencies = pom.getDependencies();
		assertEquals(1, dependencies.size());
		final Dependency dependency = dependencies.iterator().next();
		assertEquals("org.apache", dependency.getGroupId());
		assertEquals("commons-lang", dependency.getArtifactId());
		assertEquals("2.5", dependency.getVersion());
	}

	@Test
	public void testGetInstanceWithInheritedGroupId() throws Exception {
		// Invoke
		final Pom pom = invokeFactory("inherited-groupId-pom.xml");
		
		// Check
		assertGav(pom, "com.example", "child-app", "2.0");
		assertEquals("prod-sources", pom.getSourceDirectory());
		assertEquals("test-sources", pom.getTestSourceDirectory());
	}
	
	@Test
	public void testGetInstanceWithPomPackaging() throws Exception {
		// Set up
		final String pomFileName = "parent-pom.xml";
		
		// Invoke
		final Pom pom = invokeFactory(pomFileName);
		
		// Check
		assertGav(pom, "com.example", "parent-app", "3.0");
		assertEquals("pom", pom.getPackaging());
		assertEquals(Pom.DEFAULT_SOURCE_DIRECTORY, pom.getSourceDirectory());
		assertEquals(Pom.DEFAULT_TEST_SOURCE_DIRECTORY, pom.getTestSourceDirectory());
		final Collection<Module> modules = pom.getModules();
		assertEquals(2, modules.size());
		final Iterator<Module> moduleIterator = modules.iterator();
		assertModule(moduleIterator.next(), "module-one", pomFileName);
		assertModule(moduleIterator.next(), "module-two", pomFileName);
	}
	
	private void assertModule(final Module module, final String expectedName, final String pomFileName) throws Exception {
		assertEquals(expectedName, module.getName());
		final File parentPomDirectory = getPomFile(pomFileName).getParentFile();
		final File moduleDirectory = new File(parentPomDirectory, expectedName);
		final File modulePom = new File(moduleDirectory, "pom.xml");
		assertEquals(modulePom.getCanonicalPath(), module.getPomPath());
	}
}