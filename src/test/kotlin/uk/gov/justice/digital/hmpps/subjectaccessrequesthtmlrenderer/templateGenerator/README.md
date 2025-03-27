# Template Development Util
A development tool that allows users to generate SAR report HTML from stubbed service data. If you're developing a new 
template or updating an existing template you can use this tool to rapidly preview the HTML the service will create for 
your service data as you create/modify the template code.

## Getting started

### Templates
The **hmpps-subject-access-request-html-renderer** uses mustache templates to generate the report HTML. To change the 
content/format of the HTML generated for a service you must update the corresponding service template. The templates 
live under 
```
src/main/resources/templates
```
To onboard a new service add your service template to this directory. Templates **must** follow the naming convention 
`template_${SERVICE_NAME}.mustache` e.g. `template_hmpps-book-secure-move-api.mustache`

### Service Response Stubs 
The template development util requires a json response stub file in order to generate a mock report HTML document for 
the specified service. The service response stub should match the structure of the data returned by the real service's 
subject access request endpoint. The response stubs live under: 
```
src/test/resources/integration-tests.service-response-stubs
```
If a stub does not already exist for your service add a new file under this directory. Stub files **must** follow the 
naming convention `${SERVICE_NAME}-response.json` e.g. `hmpps-book-secure-move-api-response.json`

### Data transformations
Deployed instances of the HTML renderer transform certain values in the API response JSON to a user-friendly or 
sanitised version. Examples of fields include: 
- Location IDs
- User IDs
- Prison IDs

The template development tool does not have the configuration to perform these transformations so instead uses a set 
fixed values for each transformation type. For example:
- All userIds are transformed to `"Homer Simpson"`
- All prison IDs are transformed to `"HMPPS Mordor`
- And all DPS location IDs are transformed to `"Hogwarts"`

The presences of these values in the generated HTML is expected, it simply means certain fields have been transformed 
via the template helpers.

## Generating a HTML report for your service
**Prerequisites:**
- A template exists for the target service.
- A response stub json file exists for the target service.

From the project root run:
```bash
./gradlew generateReport --service=${SERVICE_NAME}
```
```
## Example
./gradlew generateReport --service=hmpps-book-secure-move-api
```
If successful the util will output a link to the generated HTML file which will open the file in your browser. 
Alternatively you can view the file under:
```
src/test/resources/integration-tests/reference-html-stubs
```