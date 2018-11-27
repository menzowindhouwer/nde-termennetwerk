package nl.knaw.huc.di.nde;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import static graphql.ExecutionInput.newExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLException;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("graphql")
public class Registry {
    
    private final String schema;
    private final ObjectMapper objectMapper;
    private final GraphQL.Builder builder;


    public Registry() throws IOException {
        this.schema = Resources.toString(Registry.class.getResource("/nl/knaw/huc/di/nde/registry/schema.graphql"), Charsets.UTF_8);
        this.objectMapper = new ObjectMapper();
        // schema
        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeRegistry = schemaParser.parse(this.schema);
        // fetching
        DataFetcher termsDataFetcher = new DataFetcher<List<TermDTO>>() {
            @Override
            public List<TermDTO> get(DataFetchingEnvironment environment) {
                List<TermDTO> terms = null;
                String match = environment.getArgument("match");
                if (match != null) {
                    terms = fetchMatchingTerms(match);
                } else {
                    terms = fetchTermsSample();
                }
                return terms;
            }
        };
        // wiring
        final RuntimeWiring runtime = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder -> builder.defaultDataFetcher(termsDataFetcher))
                .build();
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        builder = GraphQL.newGraphQL(schemaGenerator.makeExecutableSchema(typeRegistry, runtime));
    }
    
    private List<TermDTO> fetchMatchingTerms(String match) {
        System.err.println("DBG: match["+match+"]");
        return new ArrayList<TermDTO>();
    }
    
    private List<TermDTO> fetchTermsSample() {
        return new ArrayList<TermDTO>();
    }
    
    @POST
    @Consumes("application/json")
    public Response postJson(JsonNode body, @QueryParam("query") String query,
                           @HeaderParam("accept") String acceptHeader,
                           @QueryParam("accept") String acceptParam,
                           @HeaderParam("Authorization") String authHeader) {
        System.err.println("DBG: postJson");
        final String queryFromBody;
        if (body.has("query")) {
            queryFromBody = body.get("query").asText();
        } else {
            queryFromBody = null;
        }
        Map variables = null;
        if (body.has("variables")) {
            try {
                variables = objectMapper.treeToValue(body.get("variables"), HashMap.class);
            } catch (JsonProcessingException e) {
                return Response
                    .status(400)
                    .entity("'variables' should be an object node")
                    .build();
            }
        }
        final String operationName = body.has("operationName") && !body.get("operationName").isNull() ?
            body.get("operationName").asText() : null;

        return executeGraphql(query, acceptHeader, acceptParam, queryFromBody, variables, operationName, authHeader);
    }

    @POST
    @Consumes("application/graphql")
    public Response postGraphql(String query, @QueryParam("query") String queryParam,
                                @HeaderParam("accept") String acceptHeader,
                                @QueryParam("accept") String acceptParam,
                                @HeaderParam("Authorization") String authHeader) {
        System.err.println("DBG: postGraphgl");
        return executeGraphql(queryParam, acceptHeader, acceptParam, query, null, null, authHeader);
    }

    @GET
    public Response get(@QueryParam("query") String query, @HeaderParam("accept") String acceptHeader,
                        @QueryParam("accept") String acceptParam,
                        @HeaderParam("Authorization") String authHeader) {
        System.err.println("DBG: get");
        return executeGraphql(null, acceptHeader, acceptParam, query, null, null, authHeader);
    }

    public Response executeGraphql(String query, String acceptHeader, String acceptParam, String queryFromBody,
                                 Map variables, String operationName, String authHeader) {

        if (acceptParam != null && !acceptParam.isEmpty()) {
            acceptHeader = acceptParam; //Accept param overrules header because it's more under the user's control
        }
        if (unSpecifiedAcceptHeader(acceptHeader)) {
            acceptHeader = MediaType.APPLICATION_JSON;
        }
//        if (MediaType.APPLICATION_JSON.equals(acceptHeader)) {
//        } else {
//        }
        if (query != null && queryFromBody != null) {
          return Response
            .status(400)
            .type(MediaType.APPLICATION_JSON_TYPE)
            .entity("{\"errors\": [\"There's both a query as url paramater and a query in the body. Please pick one.\"]}")
            .build();
        }
        if (query == null && queryFromBody == null) {
          return Response
            .status(400)
            .type(MediaType.APPLICATION_JSON_TYPE)
            .entity("{\"errors\": [\"Please provide the graphql query as the query property of a JSON encoded object. " +
              "E.g. {query: \\\"{\\n  persons {\\n ... \\\"}\"]}")
            .build();
        }
        

        GraphQL graphQl = builder.build();

        try {
          final ExecutionResult result = graphQl
            .execute(newExecutionInput()
              .query(queryFromBody)
              .operationName(operationName)
              .variables(variables == null ? Collections.emptyMap() : variables)
              .build());
            System.err.println("DBG: result["+result+"]"); 
            return Response
              .ok()
              .type(MediaType.APPLICATION_JSON_TYPE)
              .entity(result.toSpecification())
              .build();
        } catch (GraphQLException e) {
            System.err.println("ERR: "+e.getMessage());
            return Response.status(500).entity(e.getMessage()).build();
            // throw e;
        }
  }


  public boolean unSpecifiedAcceptHeader(@HeaderParam("accept") String acceptHeader) {
    return acceptHeader == null || acceptHeader.isEmpty() || "*/*".equals(acceptHeader);
  }
    
}