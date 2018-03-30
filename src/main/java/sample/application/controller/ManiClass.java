package sample.application.controller;

//import edu.stanford.nlp.ling.CoreLabel;
//import edu.stanford.nlp.process.Morphology;
//import edu.stanford.nlp.simple.*;
//import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import opennlp.tools.lemmatizer.DictionaryLemmatizer;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSSample;
import opennlp.tools.postag.POSTaggerME;
import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.IndexWord;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.dictionary.Dictionary;
import opennlp.tools.postag.WordTagSampleStream;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sample.application.models.TagsTokensLemmas;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@RestController
public class ManiClass {

  private String tokenizerModel = "en-token.bin";
  private String posTaggerModel = "en-pos-maxent.bin";
  private String lemmatizerModel = "en-lemmatizer.txt";

  private TokenizerME tokenizer;
  private POSTaggerME posTagger;
  private DictionaryLemmatizer lemmatizer;
  private Dictionary dictionary;
  private List<String> AUX_VERBS = new ArrayList<>();

  @PostConstruct
  public void initModels() throws JWNLException  {
    dictionary = Dictionary.getDefaultResourceInstance();
    InputStream modelIn = null;
    InputStream posModelIn = null;
    InputStream dictLemmatizer = null;
    try {
      modelIn = new FileInputStream(tokenizerModel);
      TokenizerModel model = new TokenizerModel(modelIn);
      tokenizer = new TokenizerME(model);
      // Parts-Of-Speech Tagging
      // reading parts-of-speech model to a stream
      posModelIn = new FileInputStream(posTaggerModel);
      // loading the parts-of-speech model from stream
      POSModel posModel = new POSModel(posModelIn);
      // initializing the parts-of-speech tagger with model
      posTagger = new POSTaggerME(posModel);
      // loading the dictionary to input stream
      dictLemmatizer = new FileInputStream(lemmatizerModel);
      // loading the lemmatizer with dictionary
      lemmatizer = new DictionaryLemmatizer(dictLemmatizer);
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (modelIn != null) {
        try {
          modelIn.close();
        } catch (IOException e) {
        }
      }
      if (posModelIn != null) {
        try {
          posModelIn.close();
        } catch (IOException e) {
        }
      }
      if (dictLemmatizer != null) {
        try {
          dictLemmatizer.close();
        } catch (IOException e) {
        }
      }
    }
    String[] auxVerbs =
        {"do", "does", "did", "has", "have", "had", "is", "am", "are", "was", "were", "be", "being",
            "been", "may", "must", "might", "should", "could", "would", "shall", "will", "can"};
    AUX_VERBS.addAll(Arrays.asList(auxVerbs));
  }

  @RequestMapping("/tag")
  public String index(@RequestParam(name = "text") String text) {
    try {
      return similarSentences(tagAfterLemma(text));
    } catch (IOException | ClassNotFoundException | JWNLException e) {
      e.printStackTrace();
    }
    return "hi";
  }

  private TagsTokensLemmas tagAfterLemma(String text) throws IOException, ClassNotFoundException {
    TagsTokensLemmas tagsTokensLemmas = new TagsTokensLemmas();
    String tokens[] = tokenizer.tokenize(text);
    // Tagger tagging the tokens
    String tags[] = posTagger.tag(tokens);
    // finding the lemmas
    String[] lemmas = lemmatizer.lemmatize(tokens, tags);
    // printing the results
    System.out.println("\nPrinting lemmas for the given sentence...");
    System.out.println("WORD -POSTAG : LEMMA");
    for (int i = 0; i < tokens.length; i++) {
      System.out.println(tokens[i] + " -" + tags[i] + " : " + lemmas[i]);
    }
    StringBuilder res = new StringBuilder();
    for (int i = 0; i < tokens.length; i++) {
      res.append(tokens[i]).append("_").append(tags[i]).append(" ");
    }
    tagsTokensLemmas.setTokens(tokens);
    tagsTokensLemmas.setTags(tags);
    tagsTokensLemmas.setLemmas(lemmas);
    tagsTokensLemmas.setPosTaggedString(res.toString());
    return tagsTokensLemmas;
  }

  private String similarSentences(TagsTokensLemmas tagsTokensLemmas) throws JWNLException {
    Map<String, Set<String>> wordToSynonyms = new HashMap<>();
    addSynonyms("N", POS.NOUN, wordToSynonyms, tagsTokensLemmas);
    addSynonyms("J", POS.ADJECTIVE, wordToSynonyms, tagsTokensLemmas);
    addSynonyms("V", POS.VERB, wordToSynonyms, tagsTokensLemmas);
    addSynonyms("R", POS.ADVERB, wordToSynonyms, tagsTokensLemmas);
    String[] tokens = tagsTokensLemmas.getTokens();
    String[] tags = tagsTokensLemmas.getTags();
    StringBuilder res = new StringBuilder();
    for (int i = 0; i < tokens.length; i++) {
      String r1 = wordToSynonyms.getOrDefault(tokens[i], new HashSet<>()).toString();
      res.append(tokens[i]).append("_").append(tags[i]).append("_").append(r1).append(" ");
    }
    return res.toString();
  }

  private void addSynonyms(String prefix, POS pos, Map<String, Set<String>> wordToSynonyms,
      TagsTokensLemmas tagsTokensLemmas) {
    String[] tokens = tagsTokensLemmas.getTokens();
    String[] lemmas = tagsTokensLemmas.getLemmas();
    String[] tags = tagsTokensLemmas.getTags();
    for (int i = 0; i < tokens.length; i++) {
      if(tags[i].startsWith(prefix)) {
        String synonymsRoot = (lemmas[i].equalsIgnoreCase("O")) ? tokens[i]: lemmas[i];
        if(AUX_VERBS.contains(synonymsRoot.toLowerCase())){
          continue;
        }
        wordToSynonyms.put(tokens[i], getSynonyms(synonymsRoot, pos));
      }
    }
  }

  private Set<String> getSynonyms(String rootWord, POS pos) {
    Set<String> synonyms = new HashSet<>();
    try {
      IndexWord v = dictionary.getIndexWord(pos, rootWord);
      if (null != v) {
        for (long offset : v.getSynsetOffsets()) {
          Synset synset = dictionary.getSynsetAt(pos, offset);
          synset.getWords().forEach(word ->
          {
            String strWord = word.getLemma();
            if(strWord.split(" ").length <= 1) {
              synonyms.add(strWord);
            }
          });
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return synonyms;
  }

//  public String train() {
//    POSModel model = null;
//
//    InputStream dataIn = null;
//    try {
//      dataIn = new FileInputStream("en-pos.train");
//      ObjectStream<String> lineStream = new PlainTextByLineStream(dataIn, "UTF-8");
//      ObjectStream<POSSample> sampleStream = new WordTagSampleStream(lineStream);
//
//      model = POSTaggerME.train("en", sampleStream, TrainingParameters.defaultParams(), null, null);
//    }
//    catch (IOException e) {
//      // Failed to read or parse training data, training failed
//      e.printStackTrace();
//    }
//    finally {
//      if (dataIn != null) {
//        try {
//          dataIn.close();
//        }
//        catch (IOException e) {
//          // Not an issue, training already finished.
//          // The exception should be logged and investigated
//          // if part of a production system.
//          e.printStackTrace();
//        }
//      }
//    }
//  }
}
