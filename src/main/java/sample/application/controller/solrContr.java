package sample.application.controller;


import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.response.CollectionAdminResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SpellCheckResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class solrContr {

  //  private String zkHostString = "localhost:2181/solr";
  //  private SolrClient solr = new CloudSolrClient.Builder().withZkHost(zkHostString).build();
  private SolrClient solr =
      new CloudSolrClient.Builder().withSolrUrl("http://localhost:8983/solr").build();
  private static final String BASE_CONFIG_SET_NAME = "bot_config_en_spell";

  /**
   * "statement"
   * "statement_stemmed"
   * "statement_noun_adj"
   * "statement_all_verbs"
   * "statement_verbs_not_aux"
   * "statement_spell_check"
   * "statement_prefix"
   */
  private static final String QUERY =
      "(statement_noun_adj:\"{0}\"~10000)^5.5 (statement_verbs_not_aux:\"{0}\"~10000)^5.5 "
      + "(statement_all_verbs:\"{0}\"~10000)^4.5 (statement_stemmed:\"{0}\"~10000)^4.5 (statement:\"{0}\"~10000)^3.5 (statement_prefix:\"{0}\"~10000)^3"
      + "(statement_noun_adj:({0}))^3 (statement_verbs_not_aux:({0}))^3 (statement_all_verbs:({0}))^2 "
      + "(statement_stemmed:({0}))^2 (statement:({0}))^1 (statement_prefix:({0}))^1";

  @RequestMapping("/create/collection")
  public String createCollection(@RequestParam(name = "name") String name,
      @RequestParam(name = "shards") Integer numOfShards,
      @RequestParam(name = "replicas") Integer numOfReplicas) {
    return createCol(name, numOfShards, numOfReplicas);
  }

  @RequestMapping("/create/collections")
  public String createCollections(@RequestParam(name = "to") Integer to,
      @RequestParam(name = "from") Integer from) {
    StringBuilder response = new StringBuilder();
    String namePrefix = "too_many_bot_";
    Integer numOfShards=2;
    Integer numOfReplicas=1;
    for( int i=from; i<= to; i++) {
      response.append(createCol(namePrefix + i, numOfShards, numOfReplicas)).append("\n");
    }
    return response.toString();
  }

  private String createCol(String name, Integer numOfShards, Integer numOfReplicas) {

    SolrRequest<CollectionAdminResponse> createCollectionRequest = CollectionAdminRequest
        .createCollection(name, BASE_CONFIG_SET_NAME, numOfShards, numOfReplicas);

    try {
      return solr.request(createCollectionRequest).toString();
    } catch (SolrServerException | IOException e) {
      e.printStackTrace();
    }
    return "hi";
  }

  @RequestMapping("/collection/{collectionName}/add/stopword")
  public String addStopwords(@RequestParam(name = "word") String name) {

    return "";
  }

  @RequestMapping(value = "/collection/{collectionName}/add/docs", method = RequestMethod.POST)
  public String indexDoc(@PathVariable(name = "collectionName") String collection,
      @RequestBody String sentences) {
    final String[] re = {""};
    List<String> lines = Arrays.stream(sentences.split("\n")).collect(Collectors.toList());
    lines.forEach(line -> {
      String[] fields = line.split("####");
      String category = fields[0];
      String response = fields[1];
      for (int i = 2; i < fields.length; i++) {
        re[0] += indexDocToSolr(collection, category, response, fields[i]);
        re[0] += " \n ";
      }
    });
    return re[0];
  }


  @RequestMapping(value = "/collections/add/docs", method = RequestMethod.POST)
  public String indexDocsToCollections(@RequestParam(name = "to") Integer to,
      @RequestParam(name = "from") Integer from, @RequestBody String sentences) {
    final String[] re = {""};
    String namePrefix = "too_many_bot_";
    List<String> lines = Arrays.stream(sentences.split("\n")).collect(Collectors.toList());
    for( int botI=from; botI<= to; botI++) {
      String name = namePrefix + botI;
      lines.forEach(line -> {
        String[] fields = line.split("####");
        String category = fields[0];
        String response = fields[1];
        for (int i = 2; i < fields.length; i++) {
          re[0] += indexDocToSolr(name, category, response, fields[i]);
          re[0] += " \n ";
        }
      });
    }

    return re[0];
  }

  private String indexDocToSolr(String collection, String cat, String res, String ques) {
    SolrInputDocument document = new SolrInputDocument();
    document.addField("botid", "552199");
    document.addField("statement", ques);
    document.addField("answer", res);
    document.addField("category", cat);
    try {
      UpdateResponse response = solr.add(collection, document);
      solr.commit(collection);
      return response.toString();
    } catch (SolrServerException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return "";
  }

  @RequestMapping("/reload/collection")
  public String reloadCollection(@RequestParam(name = "name") String name) {
    SolrRequest<CollectionAdminResponse> reloadCollectionRequest =
        CollectionAdminRequest.reloadCollection(name);
    try {
      return solr.request(reloadCollectionRequest).toString();
    } catch (SolrServerException | IOException e) {
      e.printStackTrace();
    }
    return "hi";
  }

  @RequestMapping("/search/collection")
  public String searchCollection(@RequestParam(name = "name") String collectionName,
      @RequestParam(name = "query") String userQuery) {
    final String[] resp = {""};
    String queryStringForSolr = MessageFormat.format(QUERY, userQuery);
    SolrQuery query = new SolrQuery();
    query.setQuery(queryStringForSolr);
    query.set("wt", "json");
    query.set("indent", "on");
    query.set("fl", "score,*");
    query.set("spellcheck", "on");
    query.set("spellcheck.dictionary", "spellcheck_en");
//    query.set("spellcheck.dictionary", "wordbreak");
    query.set("spellcheck.collate", "true");
//    query.set("spellcheck.collateExtendedResults", "true");
//    query.set("spellcheck.maxCollations", "3");
    query.set("spellcheck.q", userQuery);
    try {
      QueryResponse response = solr.query(collectionName, query);
      SpellCheckResponse spellCheckResponse = response.getSpellCheckResponse();
      if(null != spellCheckResponse && null != spellCheckResponse.getCollatedResult()) {
        return "Did you mean: " + spellCheckResponse.getCollatedResult() + "\n";
      }
      response.getResults().forEach(x -> {
        resp[0] += "statement matched: " + x.getFieldValue("statement") + "\n";
        resp[0] += "score: " + x.getFieldValue("score") + "\n";
        resp[0] += "bot reply : " + x.getFieldValue("answer") + "\n\n";
      });
      return resp[0];
    } catch (SolrServerException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return "hi";
  }

  //spellcheck=on&spellcheck.dictionary=spellcheck_en&q="schoool%20feez"&spellcheck.collate=true
  private String suggest(String userQuery, String collectionName) {
    final String[] resp = {""};
    SolrQuery query = new SolrQuery();
    query.set("wt", "json");
    query.set("indent", "on");

    query.set("q", userQuery);
    try {
      QueryResponse response = solr.query(collectionName, query);

      return resp[0];
    } catch (SolrServerException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return resp[0];
  }
}
