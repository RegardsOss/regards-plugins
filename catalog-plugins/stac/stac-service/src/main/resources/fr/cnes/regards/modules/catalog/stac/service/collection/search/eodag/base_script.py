help_message = """
Download products from your project using EODAG https://github.com/CS-SI/eodag

1. If not already done, install EODAG latest version using `pip install -U eodag` or `conda update eodag`
2. Copy and paste your API key below
3. Run this script `python {{ info.filename }}`

For more information, please refer to EODAG Documentation https://eodag.readthedocs.io
"""
try:
    from eodag import EODataAccessGateway, SearchResult
    from eodag import __version__ as eodag_version
    from eodag import setup_logging
    from packaging import version

    assert version.parse(eodag_version) > version.parse("2.8.0"), help_message
except ImportError:
    print(help_message)
    exit(1)

setup_logging(1)  # 0: nothing, 1: only progress bars, 2: INFO, 3: DEBUG

dag = EODataAccessGateway()

# Please fill in the following string your API Key obtained in your {{ info.baseUri }} user settings
apikey = "PLEASE_CHANGE_ME"

# This code-block will be removed when {{ info.provider }} is publicly included to EODAG. -----------------------------------
# You can also append for once the following `{{ info.provider }}_config` string to `~/.eodag/config/eodag.yml`
#  and remove this code-block. This will also enable {{ info.provider }} provider in EODAG CLI.
#  Please note that `~/.eodag/config/eodag.yml` is automatically created on EODAG first run.
{{ info.provider }}_config = """
{{ info.provider }}:
    priority: 2 # Set highest priority. Higher value means higher priority (Default max: 1)
    search:
        type: StacSearch
        api_endpoint: {{ info.stacSearchApi }}
        need_auth: true
        pagination:
            max_items_per_page: 10_000
        metadata_mapping:
{% raw %}
            startTimeFromAscendingNode:
                - '{{"query":{{"end_datetime":{{"gte":"{startTimeFromAscendingNode#to_iso_utc_datetime}"}}}}}}'
                - '$.properties.start_datetime'
            completionTimeFromAscendingNode:
                - '{{"query":{{"start_datetime":{{"lte":"{completionTimeFromAscendingNode#to_iso_utc_datetime}"}}}}}}'
                - '$.properties.end_datetime'
{% endraw %}
    products:
        GENERIC_PRODUCT_TYPE:
            productType: '{productType}'
    download:
        type: HTTPDownload
        base_uri: {{ info.baseUri }}
        flatten_top_dirs: true
    auth:
        type: HTTPHeaderAuth
        headers:
            X-API-Key: "{apikey}"
        credentials:
            apikey: %s
""" % apikey
dag.update_providers_config({{ info.provider }}_config)
# ---------------------------------------------------------------------------------------------------------------------