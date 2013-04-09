/*******************************************************************************
 * Copyright 2010
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl-3.0.txt
 ******************************************************************************/
package de.tudarmstadt.ukp.dkpro.core.stanfordnlp;

import static org.apache.uima.util.Level.INFO;
import static org.uimafit.util.JCasUtil.select;
import static org.uimafit.util.JCasUtil.selectCovered;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Type;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;

import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS;
import de.tudarmstadt.ukp.dkpro.core.api.parameter.ComponentParameters;
import de.tudarmstadt.ukp.dkpro.core.api.resources.CasConfigurableProviderBase;
import de.tudarmstadt.ukp.dkpro.core.api.resources.MappingProvider;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;

/**
 * @author Richard Eckart de Castilho
 */
public class StanfordPosTagger
	extends JCasAnnotator_ImplBase
{
	/**
	 * Log the tag set(s) when a model is loaded.
	 * 
	 * Default: {@code false}
	 */
	public static final String PARAM_PRINT_TAGSET = ComponentParameters.PARAM_PRINT_TAGSET;
	@ConfigurationParameter(name = PARAM_PRINT_TAGSET, mandatory = true, defaultValue="false")
	protected boolean printTagSet;

	/**
	 * Use this language instead of the document language to resolve the model and tag set mapping.
	 */
	public static final String PARAM_LANGUAGE = ComponentParameters.PARAM_LANGUAGE;
	@ConfigurationParameter(name = PARAM_LANGUAGE, mandatory = false)
	protected String language;

	/**
	 * Variant of a model the model. Used to address a specific model if here are multiple models
	 * for one language.
	 */
	public static final String PARAM_VARIANT = ComponentParameters.PARAM_VARIANT;
	@ConfigurationParameter(name = PARAM_VARIANT, mandatory = false)
	protected String variant;

	/**
	 * Location from which the model is read.
	 */
	public static final String PARAM_MODEL_LOCATION = ComponentParameters.PARAM_MODEL_LOCATION;
	@ConfigurationParameter(name = PARAM_MODEL_LOCATION, mandatory = false)
	protected String modelLocation;

	/**
	 * Location of the mapping file for part-of-speech tags to UIMA types.
	 */
	public static final String PARAM_POS_MAPPING_LOCATION = ComponentParameters.PARAM_POS_MAPPING_LOCATION;
	@ConfigurationParameter(name = PARAM_POS_MAPPING_LOCATION, mandatory = false)
	protected String posMappingLocation;

	/**
	 * Use the {@link String#intern()} method on tags. This is usually a good idea to avoid
	 * spaming the heap with thousands of strings representing only a few different tags.
	 * 
	 * Default: {@code false}
	 */
	public static final String PARAM_INTERN_TAGS = ComponentParameters.PARAM_INTERN_TAGS;
	@ConfigurationParameter(name = PARAM_INTERN_TAGS, mandatory = false, defaultValue = "true")
	private boolean internStrings;

	private CasConfigurableProviderBase<MaxentTagger> modelProvider;
	private MappingProvider mappingProvider;

	@Override
	public void initialize(UimaContext aContext)
		throws ResourceInitializationException
	{
		super.initialize(aContext);

		modelProvider = new CasConfigurableProviderBase<MaxentTagger>() {
			{
				setDefault(VERSION, "20120709.0");
				setDefault(GROUP_ID, "de.tudarmstadt.ukp.dkpro.core");
				setDefault(ARTIFACT_ID,
						"de.tudarmstadt.ukp.dkpro.core.stanfordnlp-model-tagger-${language}-${variant}");
				
				setDefaultVariantsLocation(
						"de/tudarmstadt/ukp/dkpro/core/stanfordnlp/lib/tagger-default-variants.map");
				setDefault(LOCATION, "classpath:/de/tudarmstadt/ukp/dkpro/core/stanfordnlp/lib/" +
						"tagger-${language}-${variant}.properties");
				
				setOverride(LOCATION, modelLocation);
				setOverride(LANGUAGE, language);
				setOverride(VARIANT, variant);
			}
			
			@Override
			protected MaxentTagger produceResource(URL aUrl) throws IOException
			{
				try {
					MaxentTagger tagger = new MaxentTagger(aUrl.toString());
					
					if (printTagSet) {
						StringBuilder sb = new StringBuilder();
						sb.append("Model contains [").append(tagger.getTags().getSize()).append("] tags: ");
						
						List<String> tags = new ArrayList<String>();
						for (int i = 0; i < tagger.getTags().getSize(); i ++) {
							tags.add(tagger.getTags().getTag(i));
						}
						Collections.sort(tags);
						sb.append(StringUtils.join(tags, " "));
						getContext().getLogger().log(INFO, sb.toString());
					}					
					
					return tagger;
				}
				catch (ClassNotFoundException e) {
					throw new IOException(e);
				}
			}
		};
		
		mappingProvider = new MappingProvider();
		mappingProvider.setDefaultVariantsLocation(
				"de/tudarmstadt/ukp/dkpro/core/stanfordnlp/lib/tagger-default-variants.map");
		mappingProvider.setDefault(MappingProvider.LOCATION, "classpath:/de/tudarmstadt/ukp/dkpro/" +
				"core/api/lexmorph/tagset/${language}-${pos.tagset}-tagger.map");
		mappingProvider.setDefault(MappingProvider.BASE_TYPE, POS.class.getName());
		mappingProvider.setDefault("pos.tagset", "default");
		mappingProvider.setOverride(MappingProvider.LOCATION, posMappingLocation);
		mappingProvider.setOverride(MappingProvider.LANGUAGE, language);
		mappingProvider.addImport("pos.tagset", modelProvider);
	}

	@Override
	public void process(JCas aJCas)
		throws AnalysisEngineProcessException
	{
		CAS cas = aJCas.getCas();

		modelProvider.configure(cas);
		mappingProvider.configure(cas);
				
		for (Sentence sentence : select(aJCas, Sentence.class)) {
			List<Token> tokens = selectCovered(aJCas, Token.class, sentence);

			List<TaggedWord> words = new ArrayList<TaggedWord>(tokens.size());
			for (Token t : tokens) {
				words.add(new TaggedWord(t.getCoveredText()));
			}
			words = modelProvider.getResource().tagSentence(words);

			int i = 0;
			for (Token t : tokens) {
				TaggedWord tt = words.get(i);
				Type posTag = mappingProvider.getTagType(tt.tag());
				POS posAnno = (POS) cas.createAnnotation(posTag, t.getBegin(), t.getEnd());
				posAnno.setStringValue(posTag.getFeatureByBaseName("PosValue"),
						internStrings ? tt.tag().intern() : tt.tag());
				posAnno.addToIndexes();
				t.setPos((POS) posAnno);
				i++;
			}
		}
	}
}
