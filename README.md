This is SNOMED-CT 2 Grakn migrator.

To run the migrator you can use the following command:

`./snomed2grakn.sh filename.owl`

where "filename.owl" is the name of the OWL file, placed in the project's directory, that contains the SNOMED-CT ontology.

Currently, two samples of SNOMED-CT ontology are supplied with the project for testing purposes:
- snomedSample.owl, containing 400+ named classes and 282 object properties;
- snomedSample2.owl, containing 4K+ named classes and 284 object properties.

The full version of SNOMED-CT OWL ontology with 300K+ named classes and 294 object properties (including three additionally inserted inverse properties, also present in the sample files) is available at:
https://www.dropbox.com/s/obgjayguwueo7sd/snomed_ct_full_inv.owl?dl=0



TRANSLATION (please view in raw version):
------------

The migrator implements a structural translation of OWL EL subclass axioms into a Grakn graph. Additionally property axioms are translated into Grakn subtype statements or rules. Basic reasoning tasks supported by OWL EL semantics are captured by corresponding Grakn rules. The details of the translation are outlined below.


Top level Grakn ontology
------------------------

```
owl-class sub entity
	plays-role subclass
	plays-role superclass
	has-resource snomed-uri
	has-resource label;

named-class sub owl-class;

intersection-class sub owl-class;

existential-class sub owl-class;

owl-property sub relation is-abstract;

subclassing sub relation
	has-role subclass
	has-role superclass;

subclass sub role;
superclass sub role;
```

Fixed rules
-----------
```
%subclass traversal

lhs {{
(subclass: $x, superclass: $y) isa subclassing;
(subclass: $y, superclass: $z) isa subclassing;
}}
rhs {
(subclass: $x, superclass: $z) isa subclassing
};


%inhertiance of properties from superclasses

lhs {{
(subclass: $x, superclass: $y) isa subclassing;
($rel-role-1: $y, $rel-role-2: $z) isa $rel;
$rel sub owl-property;
}}
rhs {
isa $rel ($rel-role-1: $x, $rel-role-2: $z)
};
```



SNOMED properties
-----------------

Every object property is translated into a Grakn relation with two roles (called <owl-property name>-from and <owl-property name>-to) denoting the source and the target of the property, respectively, e.g.:

SNOMED:
```
<owl:ObjectProperty rdf:about="id/246112005">
    <rdfs:label xml:lang="en">Severity (attribute)</rdfs:label>
</owl:ObjectProperty>
```

Grakn:
```
"Severity-(attribute)" sub relation
	has-role "Severity-(attribute)-from"
	has-role "Severity-(attribute)-to";

owl-class plays-role "Severity-(attribute)-from";
owl-class plays-role "Severity-(attribute)-to";

"Severity-(attribute)-from" sub role;
"Severity-(attribute)-to" sub role;
```

SNOMED property axioms
----------------------

Property axioms are translated into equivalent Grakn subtype statements or rules, as follows:


- subproperties

SNOMED:
```
<owl:ObjectProperty rdf:about="property1">
    <rdfs:subPropertyOf rdf:resource="property2"/>
</owl:ObjectProperty>
```

Grakn:
```
<property1-name> sub <property2-name>;
<property1-name-from> sub <property2-name-from>;
<property1-name-to> sub <property2-name-to>;
```

- inverse properties

SNOMED:
```
<owl:ObjectProperty rdf:about="property1">
    <owl:inverseOf rdf:resource="property2" />
</owl:ObjectProperty>
```

Grakn:

```
lhs {
(<property1-name-from>: $x, <property1-name-to>: $y) isa <property1-name>;
}
rhs {
(<property2-name-from>: $y, <property2-name-to>: $x) isa <property2-name>
};

lhs {
(<property2-name-from>: $x, <property2-name-to>: $y) isa <property2-name>;
}
rhs {
(<property1-name-from>: $y, <property1-name-to>: $x) isa <property1-name>
};
```

- property chains

SNOMED:
```
<rdf:Description>
   <rdfs:subPropertyOf rdf:resource="property1"/>
   <owl:propertyChain rdf:parseType="Collection">
      <rdf:Description rdf:about="property2"/>
      <rdf:Description rdf:about="property3"/>
   </owl:propertyChain>
</rdf:Description>
```

Grakn:
```
lhs {
(<property2-name-from>: $x, <property2-name-to>: $y) isa <property2-name>;
(<property3-name-from>: $x, <property3-name-to>: $y) isa <property3-name>;
}
rhs {
(<property1-name-from>: $y, <property1-name-to>: $x) isa <property1-name>
};
```


SNOMED classes
--------------

Every OWL class is translated into a Grakn entity, with complex classes being translated recursively, according to the following rules.


- named classes

A named class is translated into a fresh instance of the `named-class` type in Grakn, with the `snomed-uri` and `label` resources defined as expected, e.g.:

SNOMED:
```
<owl:Class rdf:about="id/100014000">
   <rdfs:label xml:lang="en">BLUE SHAMPOO (product)</rdfs:label>
</owl:Class>
```

Grakn:
`$x isa named-class, has snomed-uri "snomed:100014000", has label "BLUE-SHAMPOO-(product)";`


- existential restriction

An existential restriction is translated into a fresh instance of the `existential-class` type in Grakn and related to its role filler via a corresponding Grakn relation, e.g.:

SNOMED:
```
<owl:Restriction>
	<owl:onProperty rdf:resource="property"/>
	<owl:someValuesFrom rdf:resource="filler-class"/>
</owl:Restriction>
```

Grakn:
```
$x isa existential-class;
(<property-name-from>: $x, <property-name-to>: $y) isa <property-name>;
$y id <filler-class-id>;
```

- class intersection

A class intersection is translated into a fresh instance of the `intersection-class` type in Grakn and related to all classes in the intersection via `subclassing` relation, e.g.:

SNOMED:
```
<owl:Class>
<owl:intersectionOf rdf:parseType="Collection">
       <owl:Class rdf:about="class1"/>
       <owl:Class rdf:about="class2"/>
       ...
</owl:intersectionOf>
</owl:Class>
```

Grakn:
```
$x isa intersection-class;
(subclass: $x, superclass: $y1) isa subclassing;
$y1 id <class1-id>;
(subclass: $x, superclass: $y2) isa subclassing;
$y2 id <class2-id>;
...
```

Grakn:
```
$x isa existential-class;
(<property-name-from>: $x, <property-name-to>: $y) isa <property-name>;
$y id <filler-class-id>;
```

SNOMED class axioms
-------------------

Subclass/equivalence axioms are translated into instances of relation subclassing between the corresponding classes, e.g.:

SNOMED:
```
<owl:Class rdf:about="class1">
    <owl:equivalentClass>
       <owl:Class rdf:about="class2"/>
    </owl:equivalentClass>
</owl:Class>
```

Grakn:
```
(subclass: $x, superclass: $y) isa subclassing;
(subclass: $y, superclass: $z) isa subclassing;
$x id <class1-id>;
$y id <class2-id>;
```


Graql queries over SNOMED:
--------------------------

Below is a sample of Graql queries that can be executed over the resulting knowledge graph. The queries are taken from the specification document of the SNOMED CT Expression Constraint Language - a dedicated formal language for expressing complex concepts and queries over SNOMED.

See: https://confluence.ihtsdotools.org/display/DOCECL?preview=/26840518/34865204/doc_ExpressionConstraintLanguage_v1.1.1-en-US_INT_20161118.pdf


1) lung disorders that have an associated morphology of any subtype of edema.
```
match
$x has label "Disorder-of-lung-(disorder)";
(subclass:$y, superclass:$x);
("Associated-morphology-(attribute)-from":$y, "Associated-morphology-(attribute)-to":$u);
(subclass:$u, superclass:$z);
$z has label "Edema-(morphologic-abnormality)";
```

2) clinical findings that have a finding site of any subtype of pulmonary valve structure, and an associated morphology of stenosis.
```
match
$x has label "Clinical-finding-(finding)";
(subclass:$y, superclass:$x);
("Finding-site-(attribute)-from":$y, "Finding-site-(attribute)-to":$z);
(subclass:$z, superclass:$u);
$u has label "Pulmonary-valve-structure-(body-structure)";
("Associated-morphology-(attribute)-from":$y, "Associated-morphology-(attribute)-to":$v);
$v has label "Stenosis-(morphologic-abnormality)";
```

3) any concept that has a causative agent with value paracetamol

```
match
("Causative-agent-(attribute)-from":$x, "Causative-agent-(attribute)-to":$y);
$y has label "Acetaminophen-(substance)";
```

4) clinical findings which are associated with another clinical finding that has an associated morphology of infarct (or subtype).

```
match
$x has label "Clinical-finding-(finding)";
(subclass:$y, superclass:$x);
("Associated-with-(attribute)-from":$y, "Associated-with-(attribute)-to":$z);
(subclass:$z, superclass:$x);
("Associated-morphology-(attribute)-from":$z, "Associated-morphology-(attribute)-to":$v);
(subclass:$v, superclass:$u);
$u has label "Infarct-(morphologic-abnormality)";
```

5) procedures (in fact: salpingo-oophorectomy) comprising two sub-procedures: the excision of part or all of the ovarian structure and the excision of part or all of the fallopian tube structure.

```
match
$x has label "Procedure-(procedure)";
(subclass:$y, superclass:$x);
("Method-(attribute)-from":$y, "Method-(attribute)-to":$z);
$z has label "Excision---action-(qualifier-value)";
("Procedure-site---Direct-(attribute)-from":$z, "Procedure-site---Direct-(attribute)-to":$u);
$u has label "Ovarian-structure-(body-structure)";
("Method-(attribute)-from":$y, "Method-(attribute)-to":$v);
$v has label "Excision---action-(qualifier-value)";
("Procedure-site---Direct-(attribute)-from":$v, "Procedure-site---Direct-(attribute)-to":$w);
$w has label "Fallopian-tube-structure-(body-structure)";
```

6) procedures (in fact: salpingo-oophorectomy), with laser excision of the right ovary and diathermy excision of the left fallopian tube.

```
match
$x has label "Procedure-(procedure)";
(subclass:$y, superclass:$x);
("Method-(attribute)-from":$y, "Method-(attribute)-to":$z);
$z has label "Excision---action-(qualifier-value)";
("Procedure-site---Direct-(attribute)-from":$z, "Procedure-site---Direct-(attribute)-to":$u);
$u has label "Structure-of-right-ovary-(body-structure)";
("Using-device-(attribute)-from":$u, "Using-device-(attribute)-to":$v);
$v has label "Laser-device-(physical-object)";
("Method-(attribute)-from":$y, "Method-(attribute)-to":$w);
$w has label "Diathermy-excision---action-(qualifier-value)";
("Procedure-site---Direct-(attribute)-from":$w, "Procedure-site---Direct-(attribute)-to":$r);
$r has label "Structure-of-left-fallopian-tube-(body-structure)";
```

7) a medication product that has an expression dose form, which is both a spray and a suspension:

```
match
$x has label "Pharmaceutical-\/-biologic-product-(product)";
(subclass:$y, superclass:$x);
("Has-dose-form-(attribute)-from":$y, "Has-dose-form-(attribute)-to":$z);
$z has label "Spray-dose-form-(qualifier-value)";
("Has-dose-form-(attribute)-from":$y, "Has-dose-form-(attribute)-to":$v);
$v has label "Drug-suspension-(qualifier-value)";
```

8) a procedure of surgically replacing the left hip

```
match
("Procedure-site---Indirect-(attribute)-from":$x, "Procedure-site---Indirect-(attribute)-to":$y);
$y has label "Hip-joint-structure-(body-structure)";
("Laterality-(attribute)-from":$y, "Laterality-(attribute)-to":$z);
$z has label "Left-(qualifier-value)";
("Direct-device-(attribute)-from":$x, "Direct-device-(attribute)-to":$u);
$u has label "Total-hip-replacement-prosthesis-(physical-object)";
("Method-(attribute)-from":$x, "Method-(attribute)-to":$v);
$v has label "Surgical-insertion---action-(qualifier-value)";
```

(Some of) the queries above make use of rules in order to retrieve all the matches, predominantly:

```
isa inference-rule
lhs {{
(subclass: $x, superclass: $y) isa subclassing;
($rel-role-1: $y, $rel-role-2: $z) isa $rel;
$rel sub owl-property;
} }
rhs {
($rel-role-1: $x, $rel-role-2: $z) isa $rel
};

isa inference-rule
lhs {{
("Role-group-(attribute)-from": $x, "Role-group-(attribute)-to": $y);
($rel-role-1: $y, $rel-role-2: $z) isa $rel;
$rel sub owl-property;
} }
rhs {
($rel-role-1: $x, $rel-role-2: $z) isa $rel
};

isa inference-rule
lhs { {
(subclass: $x, superclass: $y) isa subclassing;
(subclass: $y, superclass: $z) isa subclassing;
} }
rhs {
(subclass: $x, superclass: $z) isa subclassing
};

isa inference-rule
lhs { {
$x isa named-class;
} }
rhs {
(subclass: $x, superclass: $x) isa subclassing
};
```

