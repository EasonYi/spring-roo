package org.springframework.roo.addon.test;

import static org.springframework.roo.model.GoogleJavaType.GAE_LOCAL_SERVICE_TEST_HELPER;
import static org.springframework.roo.model.JdkJavaType.CALENDAR;
import static org.springframework.roo.model.JdkJavaType.DATE;
import static org.springframework.roo.model.JdkJavaType.GREGORIAN_CALENDAR;
import static org.springframework.roo.model.JdkJavaType.LIST;
import static org.springframework.roo.model.SpringJavaType.AUTOWIRED;
import static org.springframework.roo.model.SpringJavaType.CONTEXT_CONFIGURATION;
import static org.springframework.roo.model.SpringJavaType.PROPAGATION;
import static org.springframework.roo.model.SpringJavaType.TRANSACTIONAL;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.springframework.roo.addon.dod.DataOnDemandMetadata;
import org.springframework.roo.classpath.PhysicalTypeIdentifierNamingUtils;
import org.springframework.roo.classpath.PhysicalTypeMetadata;
import org.springframework.roo.classpath.details.FieldMetadata;
import org.springframework.roo.classpath.details.FieldMetadataBuilder;
import org.springframework.roo.classpath.details.MemberFindingUtils;
import org.springframework.roo.classpath.details.MethodMetadata;
import org.springframework.roo.classpath.details.MethodMetadataBuilder;
import org.springframework.roo.classpath.details.annotations.AnnotatedJavaType;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadataBuilder;
import org.springframework.roo.classpath.itd.AbstractItdTypeDetailsProvidingMetadataItem;
import org.springframework.roo.classpath.itd.InvocableMemberBodyBuilder;
import org.springframework.roo.classpath.layers.MemberTypeAdditions;
import org.springframework.roo.metadata.MetadataIdentificationUtils;
import org.springframework.roo.model.EnumDetails;
import org.springframework.roo.model.ImportRegistrationResolver;
import org.springframework.roo.model.JavaSymbolName;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.project.Path;
import org.springframework.roo.support.style.ToStringCreator;
import org.springframework.roo.support.util.Assert;
import org.springframework.roo.support.util.StringUtils;

/**
 * Metadata for {@link RooIntegrationTest}.
 * 
 * @author Ben Alex
 * @since 1.0
 */
public class IntegrationTestMetadata extends AbstractItdTypeDetailsProvidingMetadataItem {
	
	// Constants
	private static final String PROVIDES_TYPE_STRING = IntegrationTestMetadata.class.getName();
	private static final String PROVIDES_TYPE = MetadataIdentificationUtils.create(PROVIDES_TYPE_STRING);
	private static final JavaType TEST = new JavaType("org.junit.Test");
	private static final JavaType[] SETUP_PARAMETERS = {};
	private static final JavaType[] TEARDOWN_PARAMETERS = {};

	// Fields
	private IntegrationTestAnnotationValues annotationValues;
	private DataOnDemandMetadata dataOnDemandMetadata;
	private boolean isGaeSupported = false;
	private String transactionManager;
	private boolean hasEmbeddedIdentifier;
	private boolean entityHasSuperclass;
	
	public IntegrationTestMetadata(String identifier, JavaType aspectName, PhysicalTypeMetadata governorPhysicalTypeMetadata, IntegrationTestAnnotationValues annotationValues, DataOnDemandMetadata dataOnDemandMetadata, MethodMetadata identifierAccessorMethod, MethodMetadata versionAccessorMethod, MemberTypeAdditions countMethod, MemberTypeAdditions findMethod, MemberTypeAdditions findAllMethod, MemberTypeAdditions findEntriesMethod, MemberTypeAdditions flushMethod, MemberTypeAdditions mergeMethod, MemberTypeAdditions persistMethod, MemberTypeAdditions removeMethod, String transactionManager, boolean hasEmbeddedIdentifier, boolean entityHasSuperclass, boolean isGaeEnabled) {
		super(identifier, aspectName, governorPhysicalTypeMetadata);
		Assert.isTrue(isValid(identifier), "Metadata identification string '" + identifier + "' does not appear to be a valid");
		Assert.notNull(annotationValues, "Annotation values required");
		Assert.notNull(dataOnDemandMetadata, "Data on demand metadata required");

		if (!isValid()) {
			return;
		}

		this.annotationValues = annotationValues;
		this.dataOnDemandMetadata = dataOnDemandMetadata;
		this.transactionManager = transactionManager;
		this.hasEmbeddedIdentifier = hasEmbeddedIdentifier;
		this.entityHasSuperclass = entityHasSuperclass;
		
		addRequiredIntegrationTestClassIntroductions(DataOnDemandMetadata.getJavaType(dataOnDemandMetadata.getId()));

		// Add GAE LocalServiceTestHelper instance and @BeforeClass/@AfterClass methods if GAE is enabled
		if (isGaeEnabled) {
			isGaeSupported = true;
			addOptionalIntegrationTestClassIntroductions();
		}
		
		builder.addMethod(getCountMethodTest(countMethod));
		builder.addMethod(getFindMethodTest(findMethod, identifierAccessorMethod));
		builder.addMethod(getFindAllMethodTest(findAllMethod, countMethod));
		builder.addMethod(getFindEntriesMethodTest(countMethod, findEntriesMethod));
		if (flushMethod != null) {
			builder.addMethod(getFlushMethodTest(versionAccessorMethod, identifierAccessorMethod, flushMethod, findMethod));
		}
		builder.addMethod(getMergeMethodTest(mergeMethod, findMethod, flushMethod, versionAccessorMethod, identifierAccessorMethod));
		builder.addMethod(getPersistMethodTest(persistMethod, flushMethod, identifierAccessorMethod));
		builder.addMethod(getRemoveMethodTest(removeMethod, findMethod, flushMethod, identifierAccessorMethod));
		
		itdTypeDetails = builder.build();
	}
	
	/**
	 * Adds the JUnit and Spring type level annotations if needed
	 */
	private void addRequiredIntegrationTestClassIntroductions(JavaType dodGovernor) {
		// Add an @RunWith(SpringJunit4ClassRunner) annotation to the type, if the user did not define it on the governor directly
		if (MemberFindingUtils.getAnnotationOfType(governorTypeDetails.getAnnotations(), new JavaType("org.junit.runner.RunWith")) == null) {
			AnnotationMetadataBuilder runWithBuilder = new AnnotationMetadataBuilder(new JavaType("org.junit.runner.RunWith"));
			runWithBuilder.addClassAttribute("value", "org.springframework.test.context.junit4.SpringJUnit4ClassRunner");
			builder.addAnnotation(runWithBuilder);
		}
		
		// Add an @ContextConfiguration("classpath:/applicationContext.xml") annotation to the type, if the user did not define it on the governor directly
		if (MemberFindingUtils.getAnnotationOfType(governorTypeDetails.getAnnotations(), CONTEXT_CONFIGURATION) == null) {
			AnnotationMetadataBuilder contextConfigurationBuilder = new AnnotationMetadataBuilder(CONTEXT_CONFIGURATION);
			contextConfigurationBuilder.addStringAttribute("locations", "classpath:/META-INF/spring/applicationContext*.xml");
			builder.addAnnotation(contextConfigurationBuilder);
		}
		
		// Add an @Transactional, if the user did not define it on the governor directly
		if (annotationValues.isTransactional() && MemberFindingUtils.getAnnotationOfType(governorTypeDetails.getAnnotations(), TRANSACTIONAL) == null) {
			AnnotationMetadataBuilder transactionalBuilder = new AnnotationMetadataBuilder(TRANSACTIONAL);
			if (StringUtils.hasText(transactionManager) && !"transactionManager".equals(transactionManager)) {
				transactionalBuilder.addStringAttribute("value", transactionManager);
			}
			builder.addAnnotation(transactionalBuilder);
		}
	
		// Add the data on demand field if the user did not define it on the governor directly
		FieldMetadata field = MemberFindingUtils.getField(governorTypeDetails, new JavaSymbolName("dod"));
		if (field != null) {
			Assert.isTrue(field.getFieldType().equals(dodGovernor), "Field 'dod' on '" + destination.getFullyQualifiedTypeName() + "' must be of type '" + dodGovernor.getFullyQualifiedTypeName() + "'");
			Assert.notNull(MemberFindingUtils.getAnnotationOfType(field.getAnnotations(), AUTOWIRED), "Field 'dod' on '" + destination.getFullyQualifiedTypeName() + "' must be annotated with @Autowired");
		} else {
			// Add the field via the ITD
			List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
			annotations.add(new AnnotationMetadataBuilder(AUTOWIRED));
			FieldMetadataBuilder fieldBuilder = new FieldMetadataBuilder(getId(), Modifier.PRIVATE, annotations, new JavaSymbolName("dod"), dodGovernor);
			builder.addField(fieldBuilder.build());
		}
		
		ImportRegistrationResolver imports = builder.getImportRegistrationResolver();
		imports.addImport(new JavaType("org.junit.Assert"));
	}

	private void addOptionalIntegrationTestClassIntroductions() {
		// Add the GAE test helper field if the user did not define it on the governor directly
		final JavaType helperType = GAE_LOCAL_SERVICE_TEST_HELPER;
		FieldMetadata helperField = MemberFindingUtils.getField(governorTypeDetails, new JavaSymbolName("helper"));
		if (helperField != null) {
			Assert.isTrue(helperField.getFieldType().getFullyQualifiedTypeName().equals(helperType.getFullyQualifiedTypeName()), "Field 'helper' on '" + destination.getFullyQualifiedTypeName() + "' must be of type '" + helperType.getFullyQualifiedTypeName() + "'");
		} else {
			// Add the field via the ITD
			String initializer = "new LocalServiceTestHelper(new com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig())";
			FieldMetadataBuilder fieldBuilder = new FieldMetadataBuilder(getId(), Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL, new JavaSymbolName("helper"), helperType, initializer);
			builder.addField(fieldBuilder.build());
		}

		// Prepare setUp method signature
		JavaSymbolName setUpMethodName = new JavaSymbolName("setUp");
		MethodMetadata setUpMethod = getGovernorMethod(setUpMethodName, SETUP_PARAMETERS);
		if (setUpMethod != null) {
			Assert.notNull(MemberFindingUtils.getAnnotationOfType(setUpMethod.getAnnotations(), new JavaType("org.junit.BeforeClass")), "Method 'setUp' on '" + destination.getFullyQualifiedTypeName() + "' must be annotated with @BeforeClass");
		} else {
			// Add the method via the ITD
			List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
			annotations.add(new AnnotationMetadataBuilder(new JavaType("org.junit.BeforeClass")));

			InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
			bodyBuilder.appendFormalLine("helper.setUp();");

			MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(getId(), Modifier.PUBLIC | Modifier.STATIC, setUpMethodName, JavaType.VOID_PRIMITIVE, AnnotatedJavaType.convertFromJavaTypes(SETUP_PARAMETERS), new ArrayList<JavaSymbolName>(), bodyBuilder);
			methodBuilder.setAnnotations(annotations);
			builder.addMethod(methodBuilder.build());
		}

		// Prepare tearDown method signature
		JavaSymbolName tearDownMethodName = new JavaSymbolName("tearDown");
		MethodMetadata tearDownMethod = getGovernorMethod(tearDownMethodName, TEARDOWN_PARAMETERS);
		if (tearDownMethod != null) {
			Assert.notNull(MemberFindingUtils.getAnnotationOfType(tearDownMethod.getAnnotations(), new JavaType("org.junit.AfterClass")), "Method 'tearDown' on '" + destination.getFullyQualifiedTypeName() + "' must be annotated with @AfterClass");
		} else {
			// Add the method via the ITD
			List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
			annotations.add(new AnnotationMetadataBuilder(new JavaType("org.junit.AfterClass")));

			InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
			bodyBuilder.appendFormalLine("helper.tearDown();");

			MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(getId(), Modifier.PUBLIC | Modifier.STATIC, tearDownMethodName, JavaType.VOID_PRIMITIVE, AnnotatedJavaType.convertFromJavaTypes(TEARDOWN_PARAMETERS), new ArrayList<JavaSymbolName>(), bodyBuilder);
			methodBuilder.setAnnotations(annotations);
			builder.addMethod(methodBuilder.build());
		}
	}

	/**
	 * @return a test for the count method, if available and requested (may return null)
	 */
	private MethodMetadata getCountMethodTest(MemberTypeAdditions countMethod) {
		if (!annotationValues.isCount() || countMethod == null) {
			// User does not want this method
			return null;
		}

		// Prepare method signature
		JavaSymbolName methodName = new JavaSymbolName("test" + StringUtils.capitalize(countMethod.getMethodName()));
		final JavaType[] parameters = {};

		MethodMetadata method = getGovernorMethod(methodName, parameters);
		if (method != null) {
			return method;
		}
		
		List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
		annotations.add(new AnnotationMetadataBuilder(TEST));

		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine("Assert.assertNotNull(\"Data on demand for '" + annotationValues.getEntity().getSimpleTypeName() + "' failed to initialize correctly\", dod." + dataOnDemandMetadata.getRandomPersistentEntityMethod().getMethodName().getSymbolName() + "());");
		bodyBuilder.appendFormalLine("long count = " + countMethod.getMethodCall() + ";");
		bodyBuilder.appendFormalLine("Assert.assertTrue(\"Counter for '" + annotationValues.getEntity().getSimpleTypeName() + "' incorrectly reported there were no entries\", count > 0);");

		countMethod.copyAdditionsTo(builder, governorTypeDetails);

		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(getId(), Modifier.PUBLIC, methodName, JavaType.VOID_PRIMITIVE, AnnotatedJavaType.convertFromJavaTypes(parameters), new ArrayList<JavaSymbolName>(), bodyBuilder);
		methodBuilder.setAnnotations(annotations);
		return methodBuilder.build();
	}
	
	/**
	 * @return a test for the find (by ID) method, if available and requested (may return null)
	 */
	private MethodMetadata getFindMethodTest(MemberTypeAdditions findMethod, MethodMetadata identifierAccessorMethod) {
		if (!annotationValues.isFind() || findMethod == null || identifierAccessorMethod == null) {
			// User does not want this method
			return null;
		}

		// Prepare method signature
		JavaSymbolName methodName = new JavaSymbolName("test" + StringUtils.capitalize(findMethod.getMethodName()));
		final JavaType[] parameters = {};
		MethodMetadata method = getGovernorMethod(methodName, parameters);
		if (method != null) {
			return method;
		}

		ImportRegistrationResolver imports = builder.getImportRegistrationResolver();
		imports.addImport(new JavaType(identifierAccessorMethod.getReturnType().getFullyQualifiedTypeName()));

		List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
		annotations.add(new AnnotationMetadataBuilder(TEST));

		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine(annotationValues.getEntity().getSimpleTypeName() + " obj = dod." + dataOnDemandMetadata.getRandomPersistentEntityMethod().getMethodName().getSymbolName() + "();");
		bodyBuilder.appendFormalLine("Assert.assertNotNull(\"Data on demand for '" + annotationValues.getEntity().getSimpleTypeName() + "' failed to initialize correctly\", obj);");
		bodyBuilder.appendFormalLine(identifierAccessorMethod.getReturnType().getSimpleTypeName() + " id = obj." + identifierAccessorMethod.getMethodName().getSymbolName() + "();");
		bodyBuilder.appendFormalLine("Assert.assertNotNull(\"Data on demand for '" + annotationValues.getEntity().getSimpleTypeName() + "' failed to provide an identifier\", id);");
		bodyBuilder.appendFormalLine("obj = " + findMethod.getMethodCall() + ";");
		bodyBuilder.appendFormalLine("Assert.assertNotNull(\"Find method for '" + annotationValues.getEntity().getSimpleTypeName() + "' illegally returned null for id '\" + id + \"'\", obj);");
		bodyBuilder.appendFormalLine("Assert.assertEquals(\"Find method for '" + annotationValues.getEntity().getSimpleTypeName() + "' returned the incorrect identifier\", id, obj." + identifierAccessorMethod.getMethodName().getSymbolName() + "());");

		findMethod.copyAdditionsTo(builder, governorTypeDetails);

		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(getId(), Modifier.PUBLIC, methodName, JavaType.VOID_PRIMITIVE, AnnotatedJavaType.convertFromJavaTypes(parameters), new ArrayList<JavaSymbolName>(), bodyBuilder);
		methodBuilder.setAnnotations(annotations);
		return methodBuilder.build();
	}

	/**
	 * @return a test for the find all  method, if available and requested (may return null)
	 */
	private MethodMetadata getFindAllMethodTest(MemberTypeAdditions findAllMethod, MemberTypeAdditions countMethod) {
		if (!annotationValues.isFindAll() || findAllMethod == null || countMethod == null) {
			// User does not want this method, or core dependencies are missing
			return null;
		}

		// Prepare method signature
		JavaSymbolName methodName = new JavaSymbolName("test" + StringUtils.capitalize(findAllMethod.getMethodName()));
		final JavaType[] parameters = {};
		MethodMetadata method = getGovernorMethod(methodName, parameters);
		if (method != null) {
			return method;
		}

		ImportRegistrationResolver imports = builder.getImportRegistrationResolver();
		imports.addImport(LIST);

		List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
		annotations.add(new AnnotationMetadataBuilder(TEST));

		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine("Assert.assertNotNull(\"Data on demand for '" + annotationValues.getEntity().getSimpleTypeName() + "' failed to initialize correctly\", dod." + dataOnDemandMetadata.getRandomPersistentEntityMethod().getMethodName().getSymbolName() + "());");
		bodyBuilder.appendFormalLine("long count = " + countMethod.getMethodCall() + ";");
		bodyBuilder.appendFormalLine("Assert.assertTrue(\"Too expensive to perform a find all test for '" + annotationValues.getEntity().getSimpleTypeName() + "', as there are \" + count + \" entries; set the findAllMaximum to exceed this value or set findAll=false on the integration test annotation to disable the test\", count < " + annotationValues.getFindAllMaximum() + ");");
		bodyBuilder.appendFormalLine("List<" + annotationValues.getEntity().getSimpleTypeName() + "> result = " + findAllMethod.getMethodCall() + ";");
		bodyBuilder.appendFormalLine("Assert.assertNotNull(\"Find all method for '" + annotationValues.getEntity().getSimpleTypeName() + "' illegally returned null\", result);");
		bodyBuilder.appendFormalLine("Assert.assertTrue(\"Find all method for '" + annotationValues.getEntity().getSimpleTypeName() + "' failed to return any data\", result.size() > 0);");

		findAllMethod.copyAdditionsTo(builder, governorTypeDetails);
		countMethod.copyAdditionsTo(builder, governorTypeDetails);

		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(getId(), Modifier.PUBLIC, methodName, JavaType.VOID_PRIMITIVE, AnnotatedJavaType.convertFromJavaTypes(parameters), new ArrayList<JavaSymbolName>(), bodyBuilder);
		methodBuilder.setAnnotations(annotations);
		return methodBuilder.build();
	}

	/**
	 * @return a test for the find entries method, if available and requested (may return null)
	 */
	private MethodMetadata getFindEntriesMethodTest(MemberTypeAdditions countMethod, MemberTypeAdditions findEntriesMethod) {
		if (!annotationValues.isFindEntries() || countMethod == null || findEntriesMethod == null) {
			// User does not want this method, or core dependencies are missing
			return null;
		}

		// Prepare method signature
		JavaSymbolName methodName = new JavaSymbolName("test" + StringUtils.capitalize(findEntriesMethod.getMethodName()));
		final JavaType[] parameters = {};
		MethodMetadata method = getGovernorMethod(methodName, parameters);
		if (method != null) {
			return method;
		}
		
		ImportRegistrationResolver imports = builder.getImportRegistrationResolver();
		imports.addImport(LIST);

		List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
		annotations.add(new AnnotationMetadataBuilder(TEST));

		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine("Assert.assertNotNull(\"Data on demand for '" + annotationValues.getEntity().getSimpleTypeName() + "' failed to initialize correctly\", dod." + dataOnDemandMetadata.getRandomPersistentEntityMethod().getMethodName().getSymbolName() + "());");
		bodyBuilder.appendFormalLine("long count = " + countMethod.getMethodCall() + ";");
		bodyBuilder.appendFormalLine("if (count > 20) count = 20;");
		bodyBuilder.appendFormalLine("int firstResult = 0;");
		bodyBuilder.appendFormalLine("int maxResults = (int) count;");
		bodyBuilder.appendFormalLine("List<" + annotationValues.getEntity().getSimpleTypeName() + "> result = " + findEntriesMethod.getMethodCall() + ";");
		bodyBuilder.appendFormalLine("Assert.assertNotNull(\"Find entries method for '" + annotationValues.getEntity().getSimpleTypeName() + "' illegally returned null\", result);");
		bodyBuilder.appendFormalLine("Assert.assertEquals(\"Find entries method for '" + annotationValues.getEntity().getSimpleTypeName() + "' returned an incorrect number of entries\", count, result.size());");

		findEntriesMethod.copyAdditionsTo(builder, governorTypeDetails);
		countMethod.copyAdditionsTo(builder, governorTypeDetails);

		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(getId(), Modifier.PUBLIC, methodName, JavaType.VOID_PRIMITIVE, AnnotatedJavaType.convertFromJavaTypes(parameters), new ArrayList<JavaSymbolName>(), bodyBuilder);
		methodBuilder.setAnnotations(annotations);
		return methodBuilder.build();
	}

	/**
	 * @return a test for the flush method, if available and requested (may return null)
	 */
	private MethodMetadata getFlushMethodTest(MethodMetadata versionAccessorMethod, MethodMetadata identifierAccessorMethod, MemberTypeAdditions flushMethod, MemberTypeAdditions findMethod) {
		if (!annotationValues.isFlush() || versionAccessorMethod == null || identifierAccessorMethod == null || flushMethod == null || findMethod == null) {
			// User does not want this method, or core dependencies are missing
			return null;
		}

		// Prepare method signature
		JavaSymbolName methodName = new JavaSymbolName("test" + StringUtils.capitalize(flushMethod.getMethodName()));
		final JavaType[] parameters = {};
		MethodMetadata method = getGovernorMethod(methodName, parameters);
		if (method != null) {
			return method;
		}
		
		ImportRegistrationResolver imports = builder.getImportRegistrationResolver();
		imports.addImport(new JavaType(identifierAccessorMethod.getReturnType().getFullyQualifiedTypeName()));
		JavaType versionType = versionAccessorMethod.getReturnType();
		imports.addImport(versionType);

		List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
		annotations.add(new AnnotationMetadataBuilder(TEST));

		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine(annotationValues.getEntity().getSimpleTypeName() + " obj = dod." + dataOnDemandMetadata.getRandomPersistentEntityMethod().getMethodName().getSymbolName() + "();");
		bodyBuilder.appendFormalLine("Assert.assertNotNull(\"Data on demand for '" + annotationValues.getEntity().getSimpleTypeName() + "' failed to initialize correctly\", obj);");
		bodyBuilder.appendFormalLine(identifierAccessorMethod.getReturnType().getSimpleTypeName() + " id = obj." + identifierAccessorMethod.getMethodName().getSymbolName() + "();");
		bodyBuilder.appendFormalLine("Assert.assertNotNull(\"Data on demand for '" + annotationValues.getEntity().getSimpleTypeName() + "' failed to provide an identifier\", id);");
		bodyBuilder.appendFormalLine("obj = " + findMethod.getMethodCall() + ";");
		bodyBuilder.appendFormalLine("Assert.assertNotNull(\"Find method for '" + annotationValues.getEntity().getSimpleTypeName() + "' illegally returned null for id '\" + id + \"'\", obj);");
		bodyBuilder.appendFormalLine("boolean modified =  dod." + dataOnDemandMetadata.getModifyMethod().getMethodName().getSymbolName() + "(obj);");
		
		bodyBuilder.appendFormalLine(versionAccessorMethod.getReturnType().getSimpleTypeName() + " currentVersion = obj." + versionAccessorMethod.getMethodName().getSymbolName() + "();");
		bodyBuilder.appendFormalLine(flushMethod.getMethodCall() + ";");
		if (isDateOrCalendarType(versionType)) {
			bodyBuilder.appendFormalLine("Assert.assertTrue(\"Version for '" + annotationValues.getEntity().getSimpleTypeName() + "' failed to increment on flush directive\", (currentVersion != null && obj." + versionAccessorMethod.getMethodName().getSymbolName() + "().after(currentVersion)) || !modified);");
		} else {
			bodyBuilder.appendFormalLine("Assert.assertTrue(\"Version for '" + annotationValues.getEntity().getSimpleTypeName() + "' failed to increment on flush directive\", (currentVersion != null && obj." + versionAccessorMethod.getMethodName().getSymbolName() + "() > currentVersion) || !modified);");
		}
		flushMethod.copyAdditionsTo(builder, governorTypeDetails);
		findMethod.copyAdditionsTo(builder, governorTypeDetails);

		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(getId(), Modifier.PUBLIC, methodName, JavaType.VOID_PRIMITIVE, AnnotatedJavaType.convertFromJavaTypes(parameters), new ArrayList<JavaSymbolName>(), bodyBuilder);
		methodBuilder.setAnnotations(annotations);
		return methodBuilder.build();
	}

	/**
	 * @return a test for the merge method, if available and requested (may return null)
	 */
	private MethodMetadata getMergeMethodTest(MemberTypeAdditions mergeMethod, MemberTypeAdditions findMethod, MemberTypeAdditions flushMethod, MethodMetadata versionAccessorMethod, MethodMetadata identifierAccessorMethod) {
		if (!annotationValues.isMerge() || mergeMethod == null || versionAccessorMethod == null || findMethod == null || identifierAccessorMethod == null) {
			// User does not want this method, or core dependencies are missing
			return null;
		}

		// Prepare method signature
		JavaSymbolName methodName = new JavaSymbolName("test" + StringUtils.capitalize(mergeMethod.getMethodName()) + "Update");
		final JavaType[] parameterTypes = {};
		MethodMetadata method = getGovernorMethod(methodName, parameterTypes);
		if (method != null) {
			return method;
		}
		
		ImportRegistrationResolver imports = builder.getImportRegistrationResolver();
		imports.addImport(new JavaType(identifierAccessorMethod.getReturnType().getFullyQualifiedTypeName()));
		JavaType versionType = versionAccessorMethod.getReturnType();
		imports.addImport(versionType);
		
		List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
		annotations.add(new AnnotationMetadataBuilder(TEST));

		String entityName = annotationValues.getEntity().getSimpleTypeName();

		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine(entityName + " obj = dod." + dataOnDemandMetadata.getRandomPersistentEntityMethod().getMethodName().getSymbolName() + "();");
		bodyBuilder.appendFormalLine("Assert.assertNotNull(\"Data on demand for '" + entityName + "' failed to initialize correctly\", obj);");
		bodyBuilder.appendFormalLine(identifierAccessorMethod.getReturnType().getSimpleTypeName() + " id = obj." + identifierAccessorMethod.getMethodName().getSymbolName() + "();");
		bodyBuilder.appendFormalLine("Assert.assertNotNull(\"Data on demand for '" + entityName + "' failed to provide an identifier\", id);");
		bodyBuilder.appendFormalLine("obj = " + findMethod.getMethodCall() + ";");
		bodyBuilder.appendFormalLine("boolean modified =  dod." + dataOnDemandMetadata.getModifyMethod().getMethodName().getSymbolName() + "(obj);");

		bodyBuilder.appendFormalLine(versionAccessorMethod.getReturnType().getSimpleTypeName() + " currentVersion = obj." + versionAccessorMethod.getMethodName().getSymbolName() + "();");

		String castStr = entityHasSuperclass ? "(" + entityName + ")" : "";
		bodyBuilder.appendFormalLine(entityName + " merged = " + castStr + mergeMethod.getMethodCall() + ";");

		if (flushMethod != null) {
			bodyBuilder.appendFormalLine(flushMethod.getMethodCall() + ";");
			flushMethod.copyAdditionsTo(builder, governorTypeDetails);
		}
		
		bodyBuilder.appendFormalLine("Assert.assertEquals(\"Identifier of merged object not the same as identifier of original object\", merged." + identifierAccessorMethod.getMethodName().getSymbolName() + "(), id);");
		if (isDateOrCalendarType(versionType)) {
			bodyBuilder.appendFormalLine("Assert.assertTrue(\"Version for '" + entityName + "' failed to increment on merge and flush directive\", (currentVersion != null && obj." + versionAccessorMethod.getMethodName().getSymbolName() + "().after(currentVersion)) || !modified);");
		} else {
			bodyBuilder.appendFormalLine("Assert.assertTrue(\"Version for '" + entityName + "' failed to increment on merge and flush directive\", (currentVersion != null && obj." + versionAccessorMethod.getMethodName().getSymbolName() + "() > currentVersion) || !modified);");
		}
		mergeMethod.copyAdditionsTo(builder, governorTypeDetails);
		findMethod.copyAdditionsTo(builder, governorTypeDetails);

		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(getId(), Modifier.PUBLIC, methodName, JavaType.VOID_PRIMITIVE, AnnotatedJavaType.convertFromJavaTypes(parameterTypes), new ArrayList<JavaSymbolName>(), bodyBuilder);
		methodBuilder.setAnnotations(annotations);
		return methodBuilder.build();
	}

	/**
	 * @return a test for the persist method, if available and requested (may return null)
	 */
	private MethodMetadata getPersistMethodTest(MemberTypeAdditions persistMethod, MemberTypeAdditions flushMethod, MethodMetadata identifierAccessorMethod) {
		if (!annotationValues.isPersist() || persistMethod == null || identifierAccessorMethod == null) {
			// User does not want this method
			return null;
		}

		// Prepare method signature
		JavaSymbolName methodName = new JavaSymbolName("test" + StringUtils.capitalize(persistMethod.getMethodName()));
		final JavaType[] parameterTypes = {};
		MethodMetadata method = getGovernorMethod(methodName, parameterTypes);
		if (method != null) {
			return method;
		}

		List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
		annotations.add(new AnnotationMetadataBuilder(TEST));

		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine("Assert.assertNotNull(\"Data on demand for '" + annotationValues.getEntity().getSimpleTypeName() + "' failed to initialize correctly\", dod." + dataOnDemandMetadata.getRandomPersistentEntityMethod().getMethodName().getSymbolName() + "());");
		bodyBuilder.appendFormalLine(annotationValues.getEntity().getSimpleTypeName() + " obj = dod." + dataOnDemandMetadata.getNewTransientEntityMethod().getMethodName().getSymbolName() + "(Integer.MAX_VALUE);");
		bodyBuilder.appendFormalLine("Assert.assertNotNull(\"Data on demand for '" + annotationValues.getEntity().getSimpleTypeName() + "' failed to provide a new transient entity\", obj);");

		if (!hasEmbeddedIdentifier) {
			bodyBuilder.appendFormalLine("Assert.assertNull(\"Expected '" + annotationValues.getEntity().getSimpleTypeName() + "' identifier to be null\", obj." + identifierAccessorMethod.getMethodName().getSymbolName() + "());");
		}

		bodyBuilder.appendFormalLine(persistMethod.getMethodCall() + ";");
		if (flushMethod != null) {
			bodyBuilder.appendFormalLine(flushMethod.getMethodCall() + ";");
			flushMethod.copyAdditionsTo(builder, governorTypeDetails);
		}
		
		bodyBuilder.appendFormalLine("Assert.assertNotNull(\"Expected '" + annotationValues.getEntity().getSimpleTypeName() + "' identifier to no longer be null\", obj." + identifierAccessorMethod.getMethodName().getSymbolName() + "());");

		persistMethod.copyAdditionsTo(builder, governorTypeDetails);

		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(getId(), Modifier.PUBLIC, methodName, JavaType.VOID_PRIMITIVE, AnnotatedJavaType.convertFromJavaTypes(parameterTypes), new ArrayList<JavaSymbolName>(), bodyBuilder);
		methodBuilder.setAnnotations(annotations);
		return methodBuilder.build();
	}
	
	/**
	 * @return a test for the persist method, if available and requested (may return null)
	 */
	private MethodMetadata getRemoveMethodTest(MemberTypeAdditions removeMethod, MemberTypeAdditions findMethod, MemberTypeAdditions flushMethod, MethodMetadata identifierAccessorMethod) {
		if (!annotationValues.isRemove() || removeMethod == null || findMethod == null || identifierAccessorMethod == null) {
			// User does not want this method or one of its core dependencies
			return null;
		}

		// Prepare method signature
		JavaSymbolName methodName = new JavaSymbolName("test" + StringUtils.capitalize(removeMethod.getMethodName()));
		final JavaType[] parameterTypes = {};
		MethodMetadata method = getGovernorMethod(methodName, parameterTypes);
		if (method != null) {
			return method;
		}
		
		ImportRegistrationResolver imports = builder.getImportRegistrationResolver();
		imports.addImport(new JavaType(identifierAccessorMethod.getReturnType().getFullyQualifiedTypeName()));

		List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
		annotations.add(new AnnotationMetadataBuilder(TEST));
		if (isGaeSupported) {
			AnnotationMetadataBuilder transactionalBuilder = new AnnotationMetadataBuilder(TRANSACTIONAL);
			if (StringUtils.hasText(transactionManager) && !"transactionManager".equals(transactionManager)) {
				transactionalBuilder.addStringAttribute("value", transactionManager);
			}
			transactionalBuilder.addEnumAttribute("propagation", new EnumDetails(PROPAGATION, new JavaSymbolName("SUPPORTS")));
			annotations.add(transactionalBuilder);
		}

		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine(annotationValues.getEntity().getSimpleTypeName() + " obj = dod." + dataOnDemandMetadata.getRandomPersistentEntityMethod().getMethodName().getSymbolName() + "();");
		bodyBuilder.appendFormalLine("Assert.assertNotNull(\"Data on demand for '" + annotationValues.getEntity().getSimpleTypeName() + "' failed to initialize correctly\", obj);");
		bodyBuilder.appendFormalLine(identifierAccessorMethod.getReturnType().getSimpleTypeName() + " id = obj." + identifierAccessorMethod.getMethodName().getSymbolName() + "();");
		bodyBuilder.appendFormalLine("Assert.assertNotNull(\"Data on demand for '" + annotationValues.getEntity().getSimpleTypeName() + "' failed to provide an identifier\", id);");
		bodyBuilder.appendFormalLine("obj = " + findMethod.getMethodCall() + ";");
		bodyBuilder.appendFormalLine(removeMethod.getMethodCall() + ";");
		
		if (flushMethod != null) {
			bodyBuilder.appendFormalLine( flushMethod.getMethodCall() + ";");
			flushMethod.copyAdditionsTo(builder, governorTypeDetails);
		}
		
		bodyBuilder.appendFormalLine("Assert.assertNull(\"Failed to remove '" + annotationValues.getEntity().getSimpleTypeName() + "' with identifier '\" + id + \"'\", " + findMethod.getMethodCall() + ");");

		removeMethod.copyAdditionsTo(builder, governorTypeDetails);
		findMethod.copyAdditionsTo(builder, governorTypeDetails);

		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(getId(), Modifier.PUBLIC, methodName, JavaType.VOID_PRIMITIVE, AnnotatedJavaType.convertFromJavaTypes(parameterTypes), new ArrayList<JavaSymbolName>(), bodyBuilder);
		methodBuilder.setAnnotations(annotations);
		return methodBuilder.build();
	}

	private boolean isDateOrCalendarType(JavaType javaType) {
		return javaType.equals(DATE) || javaType.equals(CALENDAR) || javaType.equals(GREGORIAN_CALENDAR);
	}

	public String toString() {
		ToStringCreator tsc = new ToStringCreator(this);
		tsc.append("identifier", getId());
		tsc.append("valid", valid);
		tsc.append("aspectName", aspectName);
		tsc.append("destinationType", destination);
		tsc.append("governor", governorPhysicalTypeMetadata.getId());
		tsc.append("itdTypeDetails", itdTypeDetails);
		return tsc.toString();
	}

	public static String getMetadataIdentiferType() {
		return PROVIDES_TYPE;
	}
	
	public static String createIdentifier(JavaType javaType, Path path) {
		return PhysicalTypeIdentifierNamingUtils.createIdentifier(PROVIDES_TYPE_STRING, javaType, path);
	}

	public static JavaType getJavaType(String metadataIdentificationString) {
		return PhysicalTypeIdentifierNamingUtils.getJavaType(PROVIDES_TYPE_STRING, metadataIdentificationString);
	}

	public static Path getPath(String metadataIdentificationString) {
		return PhysicalTypeIdentifierNamingUtils.getPath(PROVIDES_TYPE_STRING, metadataIdentificationString);
	}

	public static boolean isValid(String metadataIdentificationString) {
		return PhysicalTypeIdentifierNamingUtils.isValid(PROVIDES_TYPE_STRING, metadataIdentificationString);
	}
}