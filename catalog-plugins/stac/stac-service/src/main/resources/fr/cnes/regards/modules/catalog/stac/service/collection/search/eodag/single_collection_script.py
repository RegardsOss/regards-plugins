# This command will perform a search using provided query arguments.
# - specify a collection using the `productType` key
# - add time restrictions using the `start` and `end` keys (e.g. "start": "2020-05-01" , "end": "2020-05-10T00:00:00Z",
#   UTC ISO8601 format)
# - add spatial restrictions using the "geom" key (e.g. "geom": "POLYGON ((1 43, 2 43, 2 44, 1 44, 1 43))" WKT string,
#   a bounding-box list [lonmin, latmin, lonmax, latmax] can also be passed )
# - more query arguments can be used, see
#   https://eodag.readthedocs.io/en/stable/notebooks/api_user_guide/4_search.html?#Search-parameters
search_results = dag.search_all(
    productType="{{ parameters.productType }}",
{% if parameters.start %}
    start="{{ parameters.start }}",
{% endif %}
{% if parameters.end %}
    end="{{ parameters.end }}",
{% endif %}
{% if parameters.geom %}
    geom="{{ parameters.geom }}",
{% endif %}
{% if query_parameters %}
    **{{ query_parameters|tojson(indent=2) }}
{% endif %}
)
# This command actually downloads the matching products
downloaded_paths = dag.download_all(search_results, outputs_prefix=path_out)
if downloaded_paths:
    print(f"files successfully downloaded in {downloaded_paths}")
else:
    print(f"No files downloaded! Verify API-KEY and/or product search configuration.")