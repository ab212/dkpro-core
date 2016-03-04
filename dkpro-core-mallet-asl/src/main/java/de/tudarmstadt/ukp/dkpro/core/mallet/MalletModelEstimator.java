/*******************************************************************************
 * Copyright 2016
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package de.tudarmstadt.ukp.dkpro.core.mallet;

import cc.mallet.pipe.TokenSequence2FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.TokenSequence;
import de.tudarmstadt.ukp.dkpro.core.api.featurepath.FeaturePathException;
import de.tudarmstadt.ukp.dkpro.core.api.io.JCasFileWriter_ImplBase;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.parameter.ComponentParameters;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.File;
import java.io.IOException;

/**
 * This class defines parameters and methods that are common for Mallet model estimators.
 */
public abstract class MalletModelEstimator
        extends JCasFileWriter_ImplBase
{
    /**
     * The annotation type to use for the model. Default: de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token.
     */
    public static final String PARAM_TYPE_NAME = "typeName";
    @ConfigurationParameter(name = PARAM_TYPE_NAME, mandatory = true, defaultValue = "de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token")
    private String typeName;

    /**
     * The number of threads to use during model estimation. If not set, the number of threads is determined automatically.
     */
    public static final String PARAM_NUM_THREADS = ComponentParameters.PARAM_NUM_THREADS;
    @ConfigurationParameter(name = PARAM_NUM_THREADS, mandatory = true, defaultValue = ComponentParameters.AUTO_NUM_THREADS)
    private int numThreads;

    /**
     * If set, uses lemmas instead of original text as features.
     */
    public static final String PARAM_USE_LEMMA = "useLemma";
    @ConfigurationParameter(name = PARAM_USE_LEMMA, mandatory = true, defaultValue = "false")
    private boolean useLemma;

    /**
     * Ignore tokens (or lemmas, respectively) that are shorter than the given value. Default: 3.
     */
    public static final String PARAM_MIN_TOKEN_LENGTH = "minTokenLength";
    @ConfigurationParameter(name = PARAM_MIN_TOKEN_LENGTH, mandatory = true, defaultValue = "3")
    private int minTokenLength;

    /**
     * If specific, the text contained in the given segmentation type annotations are fed as
     * separate units to the topic model estimator e.g.
     * {@code de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.sentence}. Text that is not within
     * such annotations is ignored.
     * <p>
     * By default, the full document text is used as a document.
     */
    public static final String PARAM_MODEL_ENTITY_TYPE = "modelEntityType";
    @ConfigurationParameter(name = PARAM_MODEL_ENTITY_TYPE, mandatory = false)
    private String modelEntityType;

    private InstanceList instanceList; // contains the Mallet instances

    @Override
    public void initialize(UimaContext context)
            throws ResourceInitializationException
    {
        super.initialize(context);

        numThreads = ComponentParameters.computeNumThreads(numThreads);
        getLogger().info(String.format("Using %d threads.", numThreads));
        instanceList = new InstanceList(new TokenSequence2FeatureSequence());

        /* make sure target file does not exist and create target directory */
        File targetFile = new File(getTargetLocation());
        if (targetFile.exists()) {
            throw new ResourceInitializationException(
                    new IOException(targetFile + " already exists."));
        }
        new File(getTargetLocation()).getParentFile().mkdirs();
    }

    @Override
    public void process(JCas aJCas)
            throws AnalysisEngineProcessException
    {
        DocumentMetaData metadata = DocumentMetaData.get(aJCas);
        try {
            for (TokenSequence ts : MalletUtils
                    .generateTokenSequences(aJCas, getTypeName(), useLemma(),
                            getMinTokenLength(), getModelEntityType())) {
                instanceList.addThruPipe(
                        new Instance(ts, MalletUtils.NONE_LABEL, metadata.getDocumentId(),
                                metadata.getDocumentUri()));
            }
        }
        catch (FeaturePathException e) {
            throw new AnalysisEngineProcessException(e);
        }
    }

    protected String getModelEntityType()
    {
        return modelEntityType;
    }

    protected int getMinTokenLength()
    {
        return minTokenLength;
    }

    protected boolean useLemma()
    {
        return useLemma;
    }

    protected int getNumThreads()
    {
        return numThreads;
    }

    protected String getTypeName()
    {
        return typeName;
    }

    public InstanceList getInstanceList()
    {
        return instanceList;
    }
}
