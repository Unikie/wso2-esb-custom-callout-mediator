# WSO2 ESB Custom Callout Mediator
![Build status](https://circleci.com/gh/Mystes/wso2-esb-custom-callout-mediator.svg?style=shield&circle-token=9ec921470952006994fe5cb321fdd6ae4acfd866)
## What is WSO2 ESB?
[WSO2 ESB](http://wso2.com/products/enterprise-service-bus/) is an open source Enterprise Service Bus that enables interoperability among various heterogeneous systems and business applications.

## Features
Custom Callout Mediator is an alternative to WSO2 ESB's [Callout Mediator](https://docs.wso2.com/display/ESB490/Callout+Mediator). Original mediator behaves unexpectedly in certain scenarios where you want things to be processed in sequential mode. For example in a situtation where you have the original callout mediator (blocking call) inside a sequential iterator and you make a request to a proxy containing an async request, it triggers the next loop of the iterator even if the previous was still not finished. With this Custom Callout Mediator you can create a sequential iterator safely.

This Custom Callout Mediator's all features are the same as in the original mediator's. This custom mediator was created using the base code of the Callout Mediator for WSO2 ESB 4.5.1 (with few patches), so the code base might differ from newer Callout Mediator versions. But it has been verified that in WSO2 ESB 4.8.1 the default Callout Mediator is still behaving in the same unexpected way in the described scenario above, so this custom mediator is still needed.

## Usage

### 1. Get the WSO2 ESB Vfs Mediator jar

You have two options:

a) Add as a Maven/Gradle/Ivy dependency to your project. Get the dependency snippet from [here](https://bintray.com/mystes/maven/wso2-esb-custom-callout-mediator/view).

b) Download it manually from [here](https://github.com/Mystes/wso2-esb-custom-callout-mediator/releases/tag/v1.0).

### 2. Install the mediator to the ESB
Copy the `CustomCalloutMediator-x.y.jar` to `$WSO2_ESB_HOME/repository/components/dropins/`.

### 3. Use it in your proxies/sequences
Mediator can be used as original one except the element name is customCallout instead of callout.
```xml
<customCallout serviceURL="string" | endpointKey="string" [action="string"] [initAxis2ClientOptions="boolean"]>
      <configuration [axis2xml="string"] [repository="string"]/>?
      <endpoint/>?
      <source xpath="expression" | key="string" | type="envelope" >?
      <target xpath="expression" | key="string"/>?
      <enableSec policy="string" | outboundPolicy="String" | inboundPolicy="String" />?
</customCallout>
```

#### Example
```xml
<customCallout serviceURL="http://www.mystes.fi/test/url">
      <configuration axis2xml="./repository/conf/axis2/axis2_client.xml"/>
      <source xpath="body/source">
      <target xpath="body/target"/>
</customCallout>
```

## Technical Requirements

#### Usage

* Oracle Java 6 & 7
* WSO2 ESB
    * Vfs mediator has been tested with WSO2 ESB versions 4.5.1 & 4.8.1

#### Development

* All above + Maven 3.0.X

## [License](LICENSE)

Copyright &copy; 2016 [Mystes Oy](http://www.mystes.fi). Licensed under the [Apache 2.0 License](LICENSE).
