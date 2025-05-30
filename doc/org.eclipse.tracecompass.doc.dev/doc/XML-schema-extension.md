<!--TOC-->

- [XML schema extension](#xml-schema-extension)
  - [Extending the schema](#extending-the-schema)
  - [Parsing the schema](#parsing-the-schema)
  - [Adding the extension point](#adding-the-extension-point)

<!--TOC-->

# XML schema extension

Data-driven XML analyses add a lot of possibilities to enhance Trace
Compass by developing one's own analyses and views without writing a
single line of code. It is now possible for external plugins to extend
the XSD schema to add their analysis extensions and parsers, while
taking advantage of the Trace Compass XML analysis framework.

## Extending the schema

A plugin that want to add their own element to the XSD schema can do so
by extending the *extra* element and defining a complex type extending
the base type *extraType*. Those additional elements are at the root
level of the XSD, under the *tmfxml* element. The following example
shows the XSD file for an additional *callstack* element:

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>

   <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
        attributeFormDefault="unqualified" elementFormDefault="qualified">

       <xs:element name="callstack" substitutionGroup="extra" type="callstackType"/>

   <xs:complexType name="callstackType">  
       <xs:complexContent>  
           <xs:extension base="extraType">  
              [... type definition ...]  
           </xs:extension>  
       </xs:complexContent>  
   </xs:complexType>

   </xs:schema>
```

## Parsing the schema

To do something with this new schema element, one needs to be able to
parse it. The parser must implement the *ITmfXmlSchemaParser* class.
Since the schema extension are at the XML analysis level, the expected
behavior is to define new analysis types. So the returned values of the
parser are module helpers.

The following code snippet shows an example of analysis helper created
from the *callstack* analysis defined above.

```java
   public class CallstackXmlSchemaParser implements ITmfXmlSchemaParser {

       @Override  
       public Collection<? extends IAnalysisModuleHelper> getModuleHelpers(File xmlFile, Document doc) {  
           List<IAnalysisModuleHelper> list = new ArrayList<>();  
           NodeList callstackNodes = doc.getElementsByTagName(CallstackXmlStrings.CALLSTACK);  
           for (int i = 0; i < callstackNodes.getLength(); i++) {  
               Element node = NonNullUtils.checkNotNull((Element) callstackNodes.item(i));

               IAnalysisModuleHelper helper = new CallstackXmlModuleHelper(xmlFile, node);  
               list.add(helper);  
           }  
           return list;  
       }  
   }
```

The *CallstackXmlModuleHelper* created by the parser extends the
*TmfAnalysisModuleHelperXml* class and overrides the
*TmfAnalysisModuleHelperXml#createOtherModule* method. The following
code shows an example of this.

```java
   public class CallstackXmlModuleHelper extends TmfAnalysisModuleHelperXml {

       /**  
        * Constructor  
        *  
        * @param xmlFile  
        *            The XML file this element comes from  
        * @param node  
        *            The XML element for this callstack  
        */  
       public CallstackXmlModuleHelper(File xmlFile, Element node) {  
           super(xmlFile, node, XmlAnalysisModuleType.OTHER);  
           // Specific code  
       }

       @Override  
       protected IAnalysisModule createOtherModule(@NonNull String analysisid, @NonNull String name) {  
           IAnalysisModule module = new CallstackXmlAnalysis(...);  
           module.setId(analysisid);  
           module.setName(name);  
           return module;  
       }  
   }
```

## Adding the extension point

To advertise this schema extension and parser, an
**org.eclipse.tracecompass.tmf.analysis.xml.core.xsd** extension must be
specified for the plugin.

```xml
   <extension
            point="org.eclipse.tracecompass.tmf.analysis.xml.core.xsd">  
       <xsdfile
            file="xsd_files/xmlCallstack.xsd">  
       </xsdfile>  
       <schemaParser
             class="my.package.CallstackXmlSchemaParser">  
       </schemaParser>  
   </extension>
```
