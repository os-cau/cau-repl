# MyCoRe Integration

If you compile cau-repl with GPL code enabled (the `-gpl` version), the REPL supports some additional command that
provide better integration with [MyCoRe](https://www.mycore.de) than the default generic version. As a MyCoRe user, you
should [install the REPL as a MyCoRe plugin](installation.md#mycore-plugin). In this section you will find outlined some
additional features that you can use.

------------------------------
## Retrieving Documents

There are numerous functions available for retrieving Documents from you repository. If you intend on working with a
document's XML structure, please refer to the [Working with XML](#working-with-xml) section, which describes the 
functions related to those kinds of tasks.

<a name="mcrids"></a>**Get document IDs**
> **Function**
>
> `mcrids(...selector)`
> 
> This function returns the IDs of all documents (not including derivates) that match the given `selector`.
>
> **Positional Parameters**
>
> `String | Collection<String> selector`*optional, repeatable* - The selectors to search for. Each selector parameter
> instance can either be a String or a Collection of multiple selectors. Valid selectors are MyCoRe type strings ("`mods`"), base
> strings ("`foo_mods`") and strings representing MyCoRe IDs ("`foo_mods_00000001`").<br/>
> If you omit this parameter, all documents' IDs (excluding derivates) will be returned.
>
> **Returns** a list of the `MCRObjectID` IDs from your repository's documents matching any of your selectors.

<a name="mcrderids"></a>**Get derivate IDs**
> **Function**
>
> `mcrderids(...selector)`
>
> This function returns the IDs of all derivates that match the given `selector`.
>
> **Positional Parameters**
> 
> `String | Collection<String> selector`*optional, repeatable* - The selectors to search for. Each selector parameter
> instance can either be a String or a Collection of multiple selectors. Valid selectors are MyCoRe type strings
> ("`derivate`"), base strings ("`foo_derivate`") and strings representing MyCoRe IDs ("`foo_derivate_00000001`").<br/>
> If you omit this parameter, all derivates' IDs will be returned.
>
> **Returns** a list of the `MCRObjectID` IDs from your repository's derivates matching any of your selectors.

<a name="mcrobj"></a>**Retrieving documents**
> **Function**
>
> `mcrobj(selector, filter)`
>
> This function returns all documents matching both selector and filter arguments.
>
> **Positional Parameters**
>
> `String | Collection<String> selector` *optional* - The selectors to search for. See the [mcrids()](#mcrids) function for details.
>
> `String | Closure filter` *optional* - An additional filter to apply after the selector. It can either be an XPath
> string (returning only those documents for which its result is not empty), or a Closure (returning only those
> documents for which its results is truthy).<br/>
> If you omit this parameter, exactly the documents matching the `selector` will be returned.
>
> **Returns** the list of MCRObjects matching your criteria. In the special case where your selector is an MCRObjectID
> or a valid MyCoRe ID in string form, the single result is automatically unwrapped and returned as an MCRObject without
> a list (or `null` if not matched).

<a name="mcrmods"></a>**Retrieving MODS documents**
> **Function**
>
> `mcrmods(selector, filter)`
>
> This function returns all MODS documents matching both selector and filter arguments.
>
> **Positional Parameters**
>
> `String | Collection<String> selector = "mods"` *optional* - The selectors to search for. See the [mcrids()](#mcrids)
> function for details.
>
> `String | Closure filter` *optional* - An additional filter to apply after the selector. See the [mcrobj()](#mcrobj)
> function for details.
>
> **Returns** the list of MCRMODSWrapper objects matching your criteria. In the special case where your selector is an
> MCRObjectID or a valid MyCoRe ID in string form, the single result is automatically unwrapped and returned as an
> MCRMODSWrapper without a list (or `null` if not matched).

<a name="mcrder"></a>**Retrieving derivates**
> **Function**
>
> `mcrder(selector, filter)`
>
> This function returns all derivates matching both selector and filter arguments.
>
> **Positional Parameters**
>
> `String | Collection<String> selector` *optional* - The selectors to search for. See the [mcrderids()](#mcrderids)
> function for details.
>
> `String | Closure filter` *optional* - An additional filter to apply after the selector. See the [mcrobj()](#mcrobj)
> function for details.
>
> **Returns** the list of MCRDerivate objects matching your criteria. In the special case where your selector is an
> MCRObjectID or a valid MyCoRe ID in string form, the single result is automatically unwrapped and returned as an
> MCRDerivate without a list (or `null` if not matched).

<a name="mcrstream"></a>**Streaming documents**
> **Function**
>
> `mcrstream(selector, filter)`
>
> Streams all documents matching both selector and filter arguments. Documents are loaded lazily, so you can abort the
> stream early without having to scan the entire repository.
>
> **Positional Parameters**
>
> `String | Collection<String> selector` *optional* - The selectors to search for. See the [mcrids()](#mcrids)
> function for details.
>
> `String | Closure filter` *optional* - An additional filter to apply after the selector. See the [mcrobj()](#mcrobj)
> function for details.
>
> **Returns** a Stream of MCRObject instances matching your arguments in repository default order.

<a name="mcrmodsstream"></a>**Streaming MODS documents**
> **Function**
>
> `mcrmodsstream(filter, selector)`
>
> Streams all MODS documents matching both selector and filter arguments. Documents are loaded lazily, so you can abort
> the stream early without having to scan the entire repository.
>
> **Positional Parameters**
>
> `String | Collection<String> selector = "mods"` *optional* - The selectors to search for. See the [mcrids()](#mcrids)
> function for details.
>
> `String | Closure filter` *optional* - An additional filter to apply after the selector. See the [mcrobj()](#mcrobj)
> function for details.
>
> **Returns** a Stream of MCRMODSWrapper instances matching your arguments in repository default order.

<a name="mcrderstream"></a>**Streaming derivates**
> **Function**
>
> `mcrderstream(filter, selector)`
>
> Streams all derivates matching both selector and filter arguments. Derivates are loaded lazily, so you can abort the
> stream early without having to scan the entire repository.
>
> **Positional Parameters**
>
> `String | Collection<String> selector` *optional* - The selectors to search for. See the [mcrderids()](#mcrderids)
> function for details.
>
> `String | Closure filter` *optional* - An additional filter to apply after the selector. See the [mcrobj()](#mcrobj)
> function for details.
>
> **Returns** a Stream of MCRDerivate instances matching your arguments in repository default order.

<a name="mcrderfiles"></a>**Retrieving derivates' files**
> **Function**
>
> `mcrderfiles(...selector)`
>
> Retrieve all files of the derivate(s) matching the `selector`.
>
> **Positional Parameters**
>
> `String | Collection<String> selector` *optional, repeatable* - The selectors to search for. See the [mcrderids()](#mcrderids)
> function for details.
>
> **Returns** in a single list: for each file a `Map` containing the keys `derid` (as `MCRObjectID`), `path` (as `MCRPath`) and `attributes`
> (as `MCRFileAttributes`).

The same functionality is also mirrored for Derivate instances:

> **File Mixins for org.mycore.datamodel.metadata.MCRDerivate**
>
> `derivate.files` invoked on an `MCRDerivate` instance also returns the derivate's files in the same format as [mcrderfiles()](#mcrderfiles). 


**Examples:**
```text
// generating a list of all "foo_mods" documents
groovy:000> mcrobj("foo_mods")
===> [foo_mods_00000001, foo_mods_00000002, foo_mods_00000003,
[...]

// finding the first document with a maindoc named 'sample.pdf'
groovy:000> mcrstream(null, "/mycoreobject/structure/derobjects/derobject[maindoc='sample.pdf']").findFirst()
===> Optional[foo_mods_00000101]

// generating a list of all MODS documents that were created by the admin user
groovy:000> mcrmods("mods", {it.getServiceFlag("createdby").equals("administrator")})
===> [MCRMODSWrapper(foo_mods_00000004), MCRMODSWrapper(foo_mods_00000005), MCRMODSWrapper(foo_mods_00000009), 
[...]

// find all derivates' files whose on-disk md5 checksum does not match the on-record checksum
groovy:000> mcrderfiles().findAll{ !it.path.toPhysicalPath().toFile().bytes.md5().equals(it.attributes.md5sum) }
===> []
```

------------------------------
## Working with XML

There are specialized functions available for retrieving documents if you plan to work with their XML structure
directly. In contrast to the functions that were introduced in the last section, these XML-centric functions allow you
to change the XML directly on the element-level and write your changes back to the repository after you are done.

<a name="mcrxml"></a>**Retrieving documents' XML**
> **Function**
>
> `mcrxml(selector, filter)`
>
> Retrieves the XML data of the documents matching `selector` and `filter`.
>
> **Positional Parameters**
>
> `String | Collection<String> selector` *optional* - The selectors to search for. See the [mcrids()](#mcrids)
> function for details.
>
> `String | Closure filter` *optional* - An additional filter to apply after the selector. See the [mcrobj()](#mcrobj)
> function for details.
>
> **Returns** the list of JDOM Document objects matching your criteria. In the special case where your selector is an
> MCRObjectID or a valid MyCoRe ID in string form, the single result is automatically unwrapped and returned as a JDOM
> document without a list (or `null` if not matched).

<a name="mcrderxml"></a>**Retrieving derivates' XML**
> **Function**
>
> `mcrderxml(selector, filter)`
>
> Retrieves the XML data of the derivates matching `selector` and `filter`.
>
> **Positional Parameters**
>
> `String | Collection<String> selector` *optional* - The selectors to search for. See the [mcrderids()](#mcrderids)
> function for details.
>
> `String | Closure filter` *optional* - An additional filter to apply after the selector. See the [mcrobj()](#mcrobj)
> function for details.
>
> **Returns** the list of JDOM Document objects matching your criteria. In the special case where your selector is an
> MCRObjectID or a valid MyCoRe ID in string form, the single result is automatically unwrapped and returned as a JDOM
> document without a list (or `null` if not matched).

<a name="mcrstreamxml"></a>**Streaming documents' XML**
> **Function**
>
> `mcrstreamxml(selector, filter)`
>
> Streams the XML representation of all documents matching both selector and filter arguments. Documents are loaded
> lazily, so you can abort the stream early without having to scan the entire repository.
>
> **Positional Parameters**
>
> `String | Collection<String> selector` *optional* - The selectors to search for. See the [mcrids()](#mcrids)
> function for details.
>
> `String | Closure filter` *optional* - An additional filter to apply after the selector. See the [mcrobj()](#mcrobj)
> function for details.
>
> **Returns** a Stream of JDOM Document instances matching your arguments in repository default order.

<a name="mcrderstreamxml"></a>**Streaming derivates' XML**
> **Function**
>
> `mcrderstreamxml(selector, filter)`
>
> Streams the XML representation of all derivates matching both selector and filter arguments. Derivates are loaded
> lazily, so you can abort the stream early without having to scan the entire repository.
>
> **Positional Parameters**
>
> `String | Collection<String> selector` *optional* - The selectors to search for. See the [mcrids()](#mcrderids)
> function for details.
>
> `String | Closure filter` *optional* - An additional filter to apply after the selector. See the [mcrobj()](#mcrobj)
> function for details.
>
> **Returns** a Stream of JDOM Document instances matching your arguments in repository default order.

<a name="mcrxslt"></a>**Apply an XSLT transform**
> **Function**
>
> `mcrxslt(source, ...stylesheet)`
>
> Transforms the XML document `source` with the XSLT `stylesheet` and returns the result.
> 
> *Please Note:* This function currently only sets up a simple environment for XSLT transformations. It can't - yet -
> handle MyCoRe stylesheets that need a more complex setup.
>
> **Positional Parameters**
>
> `Document | MCRObject | MCRDerivate | MCRMODSWrapper | MCRObjectID | String source` - The source XML document, which can be
> passed in a variety of formats. When you call this function with a `String`, there is a special logic in place: if the
> string is a syntactically valid MyCoRe Object ID, the Document referenced by it will be used as the source. If it is
> not a valid ID, the string's content itself will be parsed as an XML document.
>
> `String stylesheet` *optional, repeatable* - The names of the XSLT stylesheets to be applied. This function will
> resolve the names with MyCoRe's `MCRXSLTransformer.getInstance()` method, so the same naming conventions used there
> apply here. If you pass multiple instances of this parameter, the stylesheets will be applied in the same order.
> 
> **Optional Named Parameters**
> 
> `Map<String, String> params = null` - You can add some extra parameters that will be set during the XSLT
> transformation. The values from your MyCoRe configuration will always be set by default, as usual, and you don't have
> to specify them here explicitly.
>
> You can additionally use all the named parameters of the [mcrdo()](#mcrdo) function to control the session environment
> for the XSLT transformation. Without any further parameters and in absence of a pre-existing session, the REPL's
> default admin session will be used. If this function is called from a session-context, the pre-existing session will
> be reused instead.
>
> **Returns** the transformed XML as a JDOM `Document` object.

**Interacting with XML Objects**
> **XML Mixins for org.jdom2.Document**
> 
> `doc()` - Returns the full XML of `Document doc` as a `String`.
> 
> `doc[]` - Returns the structure of the root element of `Document doc` as a `String`.
> 
> `doc[xpath]` - Returns the first match of the XPath query `String xpath` on `Document doc`.
> 
> `doc[[xpath]]` - Returns all matches of the XPath query `String xpath` on `Document doc` as a list.
> 
> `doc << element` - Sets the root element of `Document doc` to `Element element`. You can alternatively pass a `String`
> in XML syntax to have it converted to an `Element` automatically.
> 
> `doc.reload()` - Reload `Document doc` from the repository in-place.
> 
> `doc.id` - Returns the ID of the MyCoRe object contained in `Document doc` as a `String`.

<!-- keep me for spacing -->
> **XML Mixins for org.jdom2.Element**
>
> `element()` - Returns the full XML of `Element element` as a `String`.
>
> `element[]` - Returns the structure of `Element element` as a `String`.
>
> `element[index]` - Returns the child of `Element element` at position `int index`. 
> 
> `element[xpath]` - Returns the first match of the XPath query `String xpath` on `Element element`.
>
> `element[[xpath]]` - Returns all matches of the XPath query `String xpath` on `Element element` as a list.
>
> `element << content` - Sets the contents of `Element element` to
> `Content | Attribute | String | Collection<Content | Attribute | String> content`.
> If you pass `String` arguments in XML-like syntax (i.e. starting with `<` and ending with `>`), they will
> automatically be converted to an `Element`. If you do not specify any namespaces in `content`, all your elements will
> inherit the namespace of `element`. Once you specify a single namespace, no automatic inheritance will be performed.
> 
> `element + content` - Appends `Content | Attribute | String | Collection<Content | Attribute | String> content` to the
> contents of `element`. The `+` operator behaves somewhat unusually in that it performs this change **in-place**,
> contrary to the usual arithmetic *plus* operator. The rules for namespace inheritance and `String` conversion are the
> same as for the `<<` operator.

To remove an element, JDOM's builtin method `Element.detach()` may be used.

> **XML Mixins for org.jdom2.Attribute**
>
> `attribute()` - Returns the value of `Attribute attribute` as a `String`.
> 
> `attribute << value` - Sets the value of `Attribute attribute` to `String value`.

To remove an attribute, JDOM's builtin method `Attribute.detach()` may be used.

> **XML Mixins for org.jdom2.Text**
>
> `text()` - Returns the value of `Text text` as a `String`.
>
> `text << value` - Sets the contents of `Text text` to `String value`.
> 
> `text + value` - Appends `String value` to the contents of `Text text`. The `+` operator behaves somewhat unusually in
> that it performs this change **in-place**, contrary to the usual arithmetic *plus* operator.

To remove a text block, JDOM's builtin method `Text.detach()` may be used.

> **XML Mixins for org.mycore.datamodel.metadata.MCRObject**
>
> `MCRObject` also mirrors most of the mixins from `org.jdom2.Document`, except for those that change the XML.

<!-- keep me for spacing -->
> **XML Mixins for org.mycore.datamodel.metadata.MCRDerivate**
>
> `MCRDerivate` also mirrors most of the mixins from `org.jdom2.Document`, except for those that change the XML.

<!-- keep me for spacing -->
> **XML Mixins for org.mycore.mods.MCRMODSWrapper**
>
> `mods.JDomDocument` - Returns the entire `Document` behind the `MCRMODSWrapper mods` (i.e. including the parts outside
> of the MODS container).
>
> `mods.createXML()` - Returns the result of invoking `createXML()` on the wrapped `MCRObject`.
>
> `MCRMODSWrapper` also mirrors most of the mixins from `org.jdom2.Document`, except for those that change the XML.

If you'd like to change a document's XML, you should use object returned by the `mcrxml()` function.

**Examples:**
```text
// retrieving XML
groovy:000> x = mcrxml("foo_mods_00000194")
===> [Document:  No DOCTYPE declaration, Root is [Element: <mycoreobject/>]]

// inspecting first level structure
groovy:000> x[]
===> [Element: <mycoreobject/>]:
       [[Text: 
         ], [Element: <structure/>], [Text: 
         ], [Element: <metadata/>], [Text: 
         ], [Element: <service/>], [Text: 
       ]]
       
// XPath queries...
groovy:000> x["//mods:title/text()"]
===> [Text: An Example Doc]
// ...or directly as a string
groovy:000> x["//mods:title/text()"]()
===> An Example Doc

// dumping full XML text
groovy:000> x["//mods:name"]()
===> 
<mods:name xmlns:mods="http://www.loc.gov/mods/v3" xmlns:xlink="http://www.w3.org/1999/xlink" type="personal" xlink:type="simple">
  <mods:role>
    <mods:roleTerm authority="marcrelator" type="code">aut</mods:roleTerm>
  </mods:role>
  <mods:displayForm>Doe, John</mods:displayForm>
  <mods:nameIdentifier type="example">foo@bar</mods:nameIdentifier>
  <mods:namePart type="family">Doe</mods:namePart>
  <mods:namePart type="given">John</mods:namePart>
</mods:name>

// updating an element with text content
groovy:000> x["//mods:title"] << "New Title"
===> [Element: <mods:title [Namespace: http://www.loc.gov/mods/v3]/>]
groovy:000> x()
[...]
          <mods:titleInfo xlink:type="simple">                                                                                      
            <mods:title>New Title</mods:title>
          </mods:titleInfo>
[...]

// adding a new child element, xml-like strings are auto-converted to xml with namespace inheritance
groovy:000> x["//mods:mods"] + "<identifier type='doi'>10.5555/12345678</identifier>"                                               
===> [Element: <mods:mods [Namespace: http://www.loc.gov/mods/v3]/>] 
groovy:000> x["//mods:mods"]()
[...]
  <mods:identifier type="doi">10.5555/12345678</mods:identifier>
</mods:mods>

// XSLT-transforming a document, overriding some parameters
groovy:000> mcrxslt("foo_mods_00000001", "xsl/mods2oai_dc.xsl", params:[WebApplicationBaseURL: "https://example.org/",
ServletsBaseURL: "https://example.org/"])
===> [Document:  No DOCTYPE declaration, Root is [Element: <record [Namespace: http://www.openarchives.org/OAI/2.0/]/>]]
groovy:000> _()
===> 
<record xmlns="http://www.openarchives.org/OAI/2.0/">
[...]
      <dc:identifier>https://example.org/receive/foo_mods_00000001</dc:identifier>
      <dc:identifier>https://example.org/MCRZipServlet/foo_derivate_00000001</dc:identifier>
[...]
```

------------------------------
## Changing documents

Here are some functions that you can use to update a document's state in the repository.

<a name="mcrsave"></a>**Update or create a document**
> **Function**
>
> `mcrsave(...object)`
>
> Creates or updates documents in the repository. The changes will be performed in a transaction. If the saved documents
> were present in one of MyCoRe's caches, those entries will automatically be invalidated.
>
> **Positional Parameters**
>
> `Document | MCRObject | MCRDerivate | MCRMODSWrapper | Collection<> object` *repeatable* - The documents to save.
> Pre-existing documents will be updated and new documents will be created. You may optionally pass your documents
> wrapped in a `Collection`.
>
> **Optional Named Parameters**
>
> `Boolean jointransaction = true` - Controls whether we will join an existing transaction. If set to `false` and
> `mcrsave()` is called from an ongoing transaction, an exception will be raised.
> 
> `Boolean reload = false` - You may optionally trigger an in-place reload for all saved documents from the repository
> after they were persisted.
> This is to ensure that your variables reflect the actual state of the document on-disk - including changes added by
> event-handlers triggered when saving. The default is to not reload the documents (which is faster, but can cause
> problems if you try to save your not-reloaded variables a second time).
> 
> `Boolean update = true` - Set this to `false` to make sure that all your documents were actually new and not
> pre-existing. In the case of pre-existing documents, an exception will be raised. By default, the pre-existing
> documents would be updated with your new version.
> 
> You can additionally use all the named parameters of the [mcrsession()](#mcrsession) function to control the session
> environment. Without any further parameters and in absence of a pre-existing session, the REPL's
> default admin session will be used to start and commit a new transaction. If the call is from a
> session-context, the pre-existing session will be reused.
>
> **Returns** nothing.

<a name="mcrdiff"></a>**Show differences between revisions of a document**
> **Function**
>
> `mcrdiff(old, updated)`
>
> Shows the XML-level differences between two versions of a document.
>
> **Positional Parameters**
>
> `Document | MCRObject | MCRDerivate | MCRMODSWrapper old` *optional* - The old version of the document. If omitted, it
> will automatically be fetched from the repository by the id of the `new` document.
>
> `Document | MCRObject | MCRDerivate | MCRMODSWrapper updated` - The new version of the document.
>
> **Returns** a `String` representation of the differences in unified diff format, or `null` if both versions are
> identical.

<a name="mcrinvalidate"></a>**Invalidate a document in MyCoRe's caches**
> **Function**
>
> `mcrinvalidate(...id)`
>
> Invalidates the specified documents in MyCoRe's Creator Cache and Permission Caches. This is required to make sure
> that changes you made to the documents are properly reflected in the system.<br/>
> If you use `mcrsave()` to update
> documents, this is done for you automatically and you do not have to call `mcrinvalidate()` explicitly.
>
> **Positional Parameters**
>
> `Document | MCRObject | MCRDerivate | MCRMODSWrapper | MCRObjectID | String | Collection<> id` *repeatable* - The
> documents whose state you want to invalidate in the caches. In addition to the usual formats, you can also pass an ID
> as a String, or a Collection of these types.
>
> **Returns** nothing.


**Examples:**
```text
groovy:000> x=mcrxml("foo_mods_00000229")
===> [Document:  No DOCTYPE declaration, Root is [Element: <mycoreobject/>]]
// let's change the document a bit
groovy:000> x["//mods:title"] << "The new title."
===> [Element: <mods:title [Namespace: http://www.loc.gov/mods/v3]/>]
groovy:000> x["//mods:physicalDescription"].detach()
===> [Element: <mods:physicalDescription [Namespace: http://www.loc.gov/mods/v3]/>]
groovy:000> x["//mods:mods"] + "<identifier type='urn'>urn:uuid:c8b34090-6cd8-11ee-b962-0242ac120002</identifier>"
===> [Element: <mods:mods [Namespace: http://www.loc.gov/mods/v3]/>]

// show the differences in the xml before saving
groovy:000> mcrdiff(x)
===> 
--- old
+++ updated
@@ -24,3 +24,3 @@
           <mods:titleInfo xlink:type="simple">
-            <mods:title>Upload Test.</mods:title>
+            <mods:title>The new title.</mods:title>
           </mods:titleInfo>
@@ -55,6 +55,3 @@
           </mods:name>
-          <mods:physicalDescription>
-            <mods:note xlink:type="simple">Datenstruktur 1&#xD;
-Datenstruktur 2</mods:note>
-          </mods:physicalDescription>
+          <mods:identifier type="urn">urn:uuid:c8b34090-6cd8-11ee-b962-0242ac120002</mods:identifier>
         </mods:mods>

// saving the changes
groovy:000> mcrsave(x, reload: true)
===> null
// note that the object was auto-reloaded in-place, reflecting all the changes applied during saving
groovy:000> x["//servdate[@type='modifydate']/text()"]()
===> 2023-10-17T11:13:31.449Z
```

------------------------------
## Solr Queries

<a name="mcrsolr"></a>**Solr Query, retrieving all matches**
> **Function**
>
> `mcrsolr(query)`
>
> Submit a query to a Solr server and return all results.
>
> **Positional Parameters**
>
> `String query` - The Solr query in [standard Solr syntax](https://solr.apache.org/guide/solr/latest/query-guide/query-syntax-and-parsers.html).
>
> **Optional Named Parameters**
> 
> `HttpSolrClient client = MCRSolrClientFactory.mainSolrClient` - The Solr client to use. If omitted, use MyCoRe's main client.
>
> `String fl` - Fields to return in [standard Solr syntax](https://solr.apache.org/guide/solr/latest/query-guide/common-query-parameters.html#fl-field-list-parameter)
> 
> `Integer rows = Integer.MAX_VALUE` - The number of matches to return. If omitted, this number is practically
> unlimited, so all matches will be returned.
> 
> `String sort` - Sorting conditions in [standard Solr syntax](https://solr.apache.org/guide/solr/latest/query-guide/common-query-parameters.html#sort-parameter).
>
> `Integer start = 0` - Include results starting with this position in the result set. If omitted, we will start at the
> beginning. 
>
> **Returns** the QueryResponse object containing your results.

<a name="mcrsolrids"></a>**SOLR Query, retrieving IDs of all matches**
> **Function**
>
> `mcrsolrids(query)`
>
> Submit a query to a Solr server and return the MyCoRe IDs of all matching documents.
>
> **Positional Parameters**
>
> `String query` - The Solr query in [standard Solr syntax](https://solr.apache.org/guide/solr/latest/query-guide/query-syntax-and-parsers.html).
>
> **Optional Named Parameters**
> 
> `HttpSolrClient client = MCRSolrClientFactory.mainSolrClient` - The Solr client to use. If omitted, use MyCoRe's main client.
>
> `Integer rows = Integer.MAX_VALUE` - The number of matches to return. If omitted, this number is practically
> unlimited, so all matches will be returned.
>
> `String sort` - Sorting conditions in [standard Solr syntax](https://solr.apache.org/guide/solr/latest/query-guide/common-query-parameters.html#sort-parameter).
>
> `Integer start = 0` - Include results starting with this position in the result set. If omitted, we will start at the
> beginning.
>
> **Returns** a `List<String>` of MyCoRe IDs matching your query.

<a name="mcrsolrfirst"></a>**SOLR Query, retrieving first match only**
> **Function**
>
> `mcrsolrfirst(query)`
>
> Submit a query to a Solr server and return the first matching result.
>
> **Positional Parameters**
>
> `String query` - The Solr query in [standard Solr syntax](https://solr.apache.org/guide/solr/latest/query-guide/query-syntax-and-parsers.html).
>
> **Optional Named Parameters**
>
> `HttpSolrClient client = MCRSolrClientFactory.mainSolrClient` - The Solr client to use. If omitted, use MyCoRe's main client.
>
> `String fl` - Fields to return in [standard Solr syntax](https://solr.apache.org/guide/solr/latest/query-guide/common-query-parameters.html#fl-field-list-parameter)
>
> `String sort` - Sorting conditions in [standard Solr syntax](https://solr.apache.org/guide/solr/latest/query-guide/common-query-parameters.html#sort-parameter).
>
> `Integer start = 0` - Return the result at this position in the result set. If omitted, we will return the first result.
>
> **Returns** the SolrDocument of your query's first match, or `null` if there were no qualifying documents.

<a name="mcrsolrstream"></a>**SOLR Query, streaming matches**
> **Function**
>
> `mcrsolrstream(query)`
>
> Submit a query to a Solr server and stream all the results incrementally, so they may be processed while the
> query is still active. You can cancel streaming queries before all results were transferred if it is convenient for you.
>
> **Positional Parameters**
>
> `String query` - The Solr query in [standard Solr syntax](https://solr.apache.org/guide/solr/latest/query-guide/query-syntax-and-parsers.html).
>
> **Optional Named Parameters**
>
> `Integer chunksize = 1000` - Size of each chunk that will be transferred vom server to client.
> 
> `HttpSolrClient client = MCRSolrClientFactory.mainSolrClient` - The Solr client to use. If omitted, use MyCoRe's main client.
>
> `String fl` - Fields to return in [standard Solr syntax](https://solr.apache.org/guide/solr/latest/query-guide/common-query-parameters.html#fl-field-list-parameter)
> 
> `Boolean parallel = false` - If enabled, this function will return a parallel stream, which may allows for faster
> processing at the price of losing ordering information. Defaults to `false`, returning a sequential, ordered stream.
>
> `String sort` - Sorting conditions in [standard Solr syntax](https://solr.apache.org/guide/solr/latest/query-guide/common-query-parameters.html#sort-parameter).
>
> **Returns** a `Stream<SolrDocument>` containing your results.

**Interacting with Solr Documents**
> **Mixins for org.apache.solr.common.SolrDocument**
> 
> Because `SolrDocument` implements Java's `Map` interface, you get access to all of
> [Groovy's convenience methods for Maps](https://docs.groovy-lang.org/latest/html/groovy-jdk/java/util/Map.html). Especially
> noteworthy are the following:
>
> `solrdoc.field` - Returns the value of the literal expression `field` contained in the Solr result `SolrDocument solrdoc`. Does not support field names
> containing special characters.
> 
> `solrdoc[field]` - Returns the value of the `String field` contained in the Solr result `SolrDocument solrdoc`. This method also supports
> field names containing special characters.

**Examples:**
```text
// find the ids of all MODS documents with "Test" in the title, recently modified documents first
groovy:000> mcrsolrids("mods.title:*Test*", sort:"modified desc")
===> [foo_mods_00000292, foo_mods_00000289, foo_mods_00000270,
[...]

// retrieve author names' of a single publication from the Solr server 
groovy:000> mcrsolrfirst("id:foo_mods_00000252")["mods.name"].join("; ")
===> Blaubär, Käptn; Blöd, Hein

// check for MyCoRe objects that exist in the Solr, but not the repository
groovy:000> `mcrsolrstream("objectKind:mycoreobject", fl: "id").map(x -> new MCRObjectID(x.id))
.filter(x -> !MCRXMLMetadataManager.instance().exists(x)).toList()`
===> []
```

------------------------------

## Sessions, Transactions, Jobs

The REPL's MyCoRe support functions handle sessions and transactions automatically for you. If you'd like to perform
longer-running batch edits, or would like to interact directly with MyCoRe's classes within a session context, there
are additional functions available that enable you to manage sessions yourself.

<a name="mcrsession"></a>**Create a MyCoRe session**
> **Function**
>
> `mcrsession()`
>
> Creates a new MyCoRe session without activating it. If invoked without parameters, it will return a default admin-level session which is
> associated with your current REPL session.
>
> **Optional Named Parameters**
>
> `MCRSession session` - Use this pre-existing session.
> 
> `String sessionid` - Use the pre-existing session identified by this id.
> 
> `MCRUser user` - Create a new session belonging to this user.
>
> `String userid` - Create a new session belonging to the user identified by this id.
> 
> **Returns** the `MCRSession` you requested.


<a name="mcrdo"></a>**Run code in a MyCoRe session / transaction**
> **Function**
>
> `mcrdo(closure)`
>
> Executes a parameterless Groovy closure inside a MyCoRe session and optionally also a transaction.
> The session is transparently entered and left at entry and exit. The transaction will automatically be committed on
> success and rolled back if an exception is thrown.
>
> **Positional Parameters**
>
> `Closure closure` - The parameterless Groovy closure that will be executed. 
>
> **Optional Named Parameters**
> 
> `Boolean join = false` - Join the active outer session instead of creating a new one. In this case, this function
> will not release the session on exit.
>
> `Boolean quiet = false` - Silence the warning that is emitted when running with an active outer session and `join = false`.
>
> `Boolean transaction = false` - Create a new transaction and commit it on exit, or roll it back if an exception is thrown.
> 
> You can additionally use all the named parameters of the [mcrsession()](#mcrsession) function to control the session
> that will be created. Without any further parameters, the REPL's default admin session will be used.
> 
> **Returns** the return value of your `closure`.

<a name="mcrjob"></a>**Create a new job**
> **Function**
>
> `mcrjob(closure)`
>
> This is a MyCoRe-enhanced version of [the REPL's universal job() function](repl.md#job). The major additional features
> that it provides are session- and transaction-management as well as integration with MyCoRe's processing
> infrastructure. You can view the progress of an `mcrjob()` in MyCoRe's web interface.
>
> **Positional Parameters**
>
> `Closure closure` - The closure to execute. See [job()](repl.md#job) for details.
>
> **Optional Named Parameters**
>
> `Boolean convert = false` - Because the job's `input` parameter must be serializable, you can't use `MCRObject`,
> `MCRDerivate` or `MCRMODSWrapper` type objects as input. When this parameter is enabled, these types of objects will
> automatically be converted to JDOM's `Document` class, which can be used instead. Note that it is generally
> recommended to pass documents' IDs as inputs and not the documents itself.
> 
> `ThreadFactory threadfactory` - This parameter analogous to its version in [job()](repl.md#job), with the added
> difference that `mcrjob()` uses it internally to manage MyCoRe session. You should refrain from setting it when using
> `mcrjob()`, or lose session and transaction handling features.
>
> `Boolean transaction = false` - Execute the job inside a MyCoRe transaction. It will be auto-committed if less than
> `transactionerrors` errors happened, or rolled back otherwise.
> 
> `Integer transactionerrors = 1` - If you set `transaction`, and the number of failed inputs reaches this limit, the
> entire job will be cancelled and the transaction rolled back. By default, this will happen on the first error.
> 
> You can additionally use all the named parameters of the [job()](repl.md#job) function as well as the
> the [mcrsession()](#mcrsession) to tweak job processing and its MyCoRe session environment. If you do not specify any
> session parameters, your closure will run inside a newly created admin-level session.
>
> **Returns** the [ReplJob](apidocs/de/uni_kiel/rz/fdr/repl/REPLJob.html) that was created.

**Related Classes:** [REPLJob](apidocs/de/uni_kiel/rz/fdr/repl/REPLJob.html), [REPLJobCallbackAutoTune](apidocs/de/uni_kiel/rz/fdr/repl/REPLJobCallbackAutoTune.html) provide additional functionality related to job
control.

**Examples:**
```text
// sessions can be nested
groovy:000> mcrdo({
groovy:001>   println(MCRSessionMgr.currentSession)
groovy:002>   mcrdo({ println(MCRSessionMgr.currentSession) }, userid: "editor", quiet: true)
groovy:003>   println(MCRSessionMgr.currentSession)
groovy:004> })
MCRSession[c86a247f-ef95-4a20-853f-d27e9a935dc9,user:'administrator',ip:127.0.0.1]
MCRSession[17a156c4-613c-4453-8ab7-71302a58bed0,user:'editor',ip:127.0.0.1]
MCRSession[c86a247f-ef95-4a20-853f-d27e9a935dc9,user:'administrator',ip:127.0.0.1]

// bulk editing in a transaction. for a speedup, try adding "concurrency: 5" to the parameters
// THIS EXAMPLE WILL MAKE PERMANENT CHANGES TO YOUR DOCUMENTS
groovy:000> ids = mcrsolrids("mods.title:*Test*")
===> [foo_mods_00000272, foo_mods_00000273, foo_mods_00000274,
[...]
groovy:000> mcrjob({ id, job ->
groovy:001>   def doc = mcrxml(id)
groovy:002>   def title = doc["//mods:title"]
groovy:003>   title << title.text.replaceAll(/(?i)Test/, "Bork Bork Bork")
groovy:004>   mcrsave(doc)
groovy:005>   return title.text
groovy:006> }, inputs: ids, transaction: true, progress: true)
===> 20231017-125903-180455901 (Job 20231017-125903-180455901)
[2023-10-17 12:59:03] INFO Job 20231017-125903-180455901: 4% done, eta 52s
[...]                            
```

------------------------------
## MyCoRe's native CLI

MyCoRe's native CLI commands can be invoked from the REPL. Some of MyCoRe's CLI commands implicitly assume that there
is only one CLI session active globally, so do not execute multiple commands in parallel (especially not in a parallelized job).

<a name="mcrcli"></a>**Use MyCoRe's native CLI**
> **Function**
>
> `mcrcli(command)`
>
> The REPL integrates MyCoRe's native CLI, which you can access from the SSH session.<br/>
> *Please Note:* Some of MyCoRe's CLI commands implicitly assume that there is only one CLI session active globally, so
> do not execute multiple commands in parallel (especially not in a parallelized job).
> 
> **Positional Parameters**
> 
> `String command` - The command to execute, exactly as you would type it in MyCoRe's native CLI.
>
> **Optional Named Parameters**
>
> `Boolean errors_from_log = true` - If enabled, log messages with the level `ERROR` or above will be treated as a
> failed CLI command. Many MyCoRe CLI commands do not throw exceptions when an error occurs, so this is necessary to
> detect failure conditions.
> 
> `MCRSession mcrsession = <the REPL's default admin session>` - You can optionally specify a MyCoRe session that your
> command will run in. If omitted, the REPL's default administrator-level session will be used.
> 
> **Returns** `true` if there were no errors, `false` if there were errors. Further details are available in the
> `cauCLIResult` MyCoRe session variable (access it e.g. by `mcrsession().get("cauCLIResult")`).<br>

> **Shell Command**
>
> `:mcrcli [command]`<br/>
> `:M [command]`
>
> This is the shell command version of the [mcrcli()](#mcrcli) function. It provides identical functionality, and is more
> convenient to work with since it does not require you to quote your command.<br/>
> *Please Note:* for technical reasons, repeated whitespace characters in your command string will be merged into a
> single space - even within quotes (which will be considered part of your command). If this is a problem, you should
> use the [mcrcli()](#mcrcli) function instead.
>
> **Returns** `true` if there were no errors, `false` if there were errors. Further details are available in the
> `cauCLIResult` MyCoRe session variable (access it e.g. by `mcrsession().get("cauCLIResult")`).<br/>

**Examples:**
```text
// let's select some documents
groovy:000> :M select objects with solr query id:foo_mods_00000001 in core main
Syntax matched (executed): select objects with solr query {0} in core {1}
1 objects selected
CLI finished successfully: 1 commands
Hint: retrieve detailed results from the "cauCLIResult" session variable, e.g.: mcrsession().get("cauCLIResult")
===> true
===> true
groovy:000> :M list selected
Syntax matched (executed): list selected
List selected MCRObjects
foo_mods_00000001 
CLI finished successfully: 1 commands
Hint: retrieve detailed results from the "cauCLIResult" session variable, e.g.: mcrsession().get("cauCLIResult")
===> true
===> true

// let's now intentionally trigger an error
groovy:000> :M set main file of invalid_id to foo
Syntax matched (executed): set main file of {0} to {1}
invalid_id is not valid. 
ERROR: Command 'set main file of invalid_id to foo' failed
java.lang.RuntimeException: invalid_id is not valid.
CLI finished with 1 errors
Hint: retrieve detailed results from the "cauCLIResult" session variable, e.g.: mcrsession().get("cauCLIResult")
ERROR java.lang.RuntimeException:
invalid_id is not valid.
        at groovysh_evaluate.mcrcli (groovysh_evaluate:31)
        at groovysh_evaluate.mcrcli (groovysh_evaluate)
// we can retrieve detailed results from the session variable
groovy:000> mcrsession().get("cauCLIResult")
===> [[cmd:set main file of invalid_id to foo, log:Syntax matched (executed): set main file of {0} to {1}
invalid_id is not valid. 
, error:java.lang.RuntimeException: invalid_id is not valid., timestamp:2023-10-10T11:19:39.861122497Z]]
```
------------------------------

## Compiling
The REPL will normally compile your Groovy sources when the target application starts. If you want to compile code
dynamically at runtime, there is a support function available for you.

<a name="mcrcompile"></a>**Compile Groovy sources**
> **Function**
>
> `mcrcompile(path)`
>
> Compile the Groovy sources at the specified path and load them into a suitable classloader. This is a specialized
> version of the generic [compile()](repl.md#compile) function which will automatically use the proper ClassLoader and
> class path of your servlet container.
> 
> **Positional Parameters**
>
> `Path | File | String path` - The location of the sources to compile. If you pass a directory, all `.groovy` files
> below it will be compiled as one coherent unit.
>
> **Optional Named Parameters**
> 
> `ClassLoader classloader = <MyCoRe's classloader>` - The ClassLoader in which the generated classed should be put. Will
> default to MyCoRe's ClassLoader.
>
> `String classpath = <MyCoRe's classpath>` - The class path to use when locating targets of the @Patches annotation.
> Defaults to the MyCoRe's classpath.
>
> **Returns** the [GroovySourceDirectory](apidocs/de/uni_kiel/rz/fdr/repl/groovy/GroovySourceDirectory.html) with the results of the compilation.

**Related Classes:** [GroovySourceDirectory](apidocs/de/uni_kiel/rz/fdr/repl/groovy/GroovySourceDirectory.html) provides additional functionality related to compilation.