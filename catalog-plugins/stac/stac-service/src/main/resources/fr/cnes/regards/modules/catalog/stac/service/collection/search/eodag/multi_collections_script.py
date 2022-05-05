# Use this code-block to define your search criteria. It defines a list of query-arguments dictionnaries.
# Each query-arguments dictionnary will be used to perform a distinct search, whose results will then be contatenated.
# - add/remove collections using the `productType` key (one per query-arguments dictionnary)
# - add time restrictions using the `start` and `end` keys (e.g. "start": "2020-05-01" , "end": "2020-05-10T00:00:00Z",
#   UTC ISO8601 format)(one per collection/dictionnary)
# - add spatial restrictions using the "geom" key (e.g. "geom": "POLYGON ((1 43, 2 43, 2 44, 1 44, 1 43))" WKT string,
#   a bounding-box list [lonmin, latmin, lonmax, latmax] can also be passed )(one per collection/dictionnary)
# - more query arguments can be used, see
#   https://eodag.readthedocs.io/en/stable/notebooks/api_user_guide/4_search.html?#Search-parameters
project_query_args = [
{% for collection_parameters in parameters %}
    {{ collection_parameters|tojson}}{% if !loop.last %},
    {% endif %}
{% endfor %}
]

# This code-block searches for matching products in {{ info.portalName }} catalog. No need to modify.
project_search_results = SearchResult([])
for query_args in project_query_args:
    project_search_results.extend(dag.search_all(**query_args))
# This command actually downloads the matching products
downloaded_paths = dag.download_all(project_search_results)
