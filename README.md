# SMART CDS

_Example Clinical Decision Support services implemented using onfhir-cds library ([onFHIR.io](https://onfhir.io/))._

OnFHIR CDS library provides a standalone server to implement CDS-Hooks compliant Clinical Decision Support Services.
Users can configure their services following the CDS-Hooks specs, implement the logic and bind them to the server.
The server can be easily integrated with [HL7 FHIR](https://www.hl7.org/fhir/) servers that supports the
[Smart on FHIR](https://docs.smarthealthit.org/) standards.

In this example, some risk prediction services are implemented such as QRISK and SCORE. The services also provides some
recommendations based on NICE guidelines to lower the predicted risks.

You can check the following [project](https://github.com/srdc/smart-on-fhir-web-apps.git) to see the GUIs for these services implemented by [Angular](https://angular.io).

## Features

- Easy integration with existing health systems which supports HL7 FHIR
- CDS-Hooks compliant
- New CDS Services can be added easily by simple configurations

## Build and Run

```bash
git clone https://github.com/srdc/smart-on-fhir-cds.git
cd smart-cds
mvn package
java -jar target/smart-cds.jar
```

## Endpoints

### Discovery Endpoint

```
GET http://localhost:8084/cds-hooks/cds-services
```
```
{
  "services": [
    {
      "hook": "calculate",
      "title": "QRISK service",
      "description": "",
      "id": "qrisk",
      "prefetch": {
        "Type1Diabetes": "Condition?patient=Patient/{{context.patientId}}&code=http://hl7.org/fhir/sid/icd-10|E10&_count=1",
        ...,
        "patient": "Patient/{{context.patientId}}"
      }
    },
    {
      "hook": "calculate",
      "title": "QRISK3 service",
      "description": "",
      "id": "qrisk3",
      "prefetch": {
        "Type1Diabetes": "Condition?patient=Patient/{{context.patientId}}&code=http://hl7.org/fhir/sid/icd-10|E10&_count=1",
        ...,
        "patient": "Patient/{{context.patientId}}"
      }
    },
    {
      "hook": "calculate",
      "title": "SCORE2 service",
      "description": "",
      "id": "score2",
      "prefetch": {
        "HDL": "Observation?patient=Patient/{{context.patientId}}&code=http://loinc.org|2085-9&_sort=-date&_count=1",
        "Region": "Observation?patient=Patient/{{context.patient}}&code=http://srdc.com.tr/custom|score2-region&_sort=-date&_count=1",
        "TotalCholesterol": "Observation?patient=Patient/{{context.patientId}}&code=http://loinc.org|2093-3&_sort=-date&_count=1",
        "SmokingStatus": "Observation?patient=Patient/{{context.patientId}}&code=http://loinc.org|72166-2&_sort=-date&_count=1",
        "BP_SBP": "Observation?patient=Patient/{{context.patientId}}&code=http://loinc.org|8480-6,http://loinc.org|55284-4&_sort=-date&_count=1",
        "patient": "Patient/{{context.patientId}}"
      }
    },
    {
      "hook": "calculate",
      "title": "ACC/AHA Service",
      "description": "",
      "id": "acc_aha",
      "prefetch": {
        "Type1Diabetes": "Condition?patient=Patient/{{context.patientId}}&code=http://hl7.org/fhir/sid/icd-10|E10&_count=1",
        ...,
        "patient": "Patient/{{context.patientId}}"
      }
    }
  ]
}

```

### Service Endpoint

```
POST http://localhost:8084/cds-hooks/cds-services/qrisk
```

The prefetch data required by the CDS service can be provided directly in the request body:

```
{
   "hook":"calculate",
   "hookInstance":"9697e835-27eb-405f-825e-06dc66ba7638",
   "context":{
      "patientId":"5497771"
   },
   "prefetch":{
      "Type1Diabetes":{
         "resourceType":"Bundle",
         "id":"1722002375416-bundle",
         "total":0,
         "type":"searchset",
         "entry":[
            
         ]
      },
      "HDL":{
         "resourceType":"Bundle",
         "id":"1722002375416-bundle",
         "total":1,
         "type":"searchset",
         "entry":[
            {
               "resource":{
                  "resourceType":"Observation",
                  "id":"1722002375416-observation",
                  "effectiveDateTime":"2024-07-26T13:59:35.416Z",
                  "code":{
                     "coding":[
                        {
                           "system":"http://loinc.org",
                           "code":"2085-9",
                           "display":"HDL cholesterol"
                        }
                     ]
                  },
                  "subject":{
                     "reference":"Patient/5497771"
                  },
                  "valueQuantity":{
                     "value":50,
                     "unit":"mg/dL",
                     "code":"mg/dL",
                     "system":"http://unitsofmeasure.org"
                  },
                  ...
               },
               "search":{
                  "mode":"match"
               }
            }
         ]
      },
      ...,
      "patient":{
         "resourceType":"Patient",
         "id":"5497771",
         ...
      }
   }
}
```

Or onfhir-cds can also fetch the prefetches from the specified FHIR server if they're not provided in the request:

```
{
   "hook":"calculate",
   "hookInstance":"9697e835-27eb-405f-825e-06dc66ba7638",
   "context":{
      "patientId":"5497771"
   },
   "prefetch":{},
   "fhirServer":"https://some-fhir-server/r4/fhir",
   "fhirAuthorization": {
     "access_token": "some-access-token",
     "token_type": "Bearer",
     "expires_in": 300,
     "scope": "user/Patient.read user/Observation.read",
     "subject": "cds-service4"
   }
}
```

Response:
```
{
    "cards": [
        {
            "uuid": "CVD CARD SCORE",
            "summary": "QRisk SCORE",
            "detail": "",
            "indicator": "no-show",
            "source": {
                "label": "",
                "url": ""
            },
            "suggestions": [
                {
                    "label": "QRisk SCORE",
                    "uuid": "Suggestion 1",
                    "actions": [
                        {
                            "type": "create",
                            "description": "",
                            "resource": {
                                "resourceType": "Observation",
                                "subject": {
                                    "reference": "Patient/5497771"
                                },
                                "status": "final",
                                "effectiveDateTime": "2024-07-26T13:59:35.430Z",
                                "code": {
                                    "coding": [
                                        {
                                            "system": "http://snomed.info/sct",
                                            "code": "718087004",
                                            "display": "QRISK2 cardiovascular disease 10 year risk score"
                                        }
                                    ]
                                },
                                "extension": [
                                    {
                                        "url": "https://kroniq.srdc.com.tr/fhir/StructureDefinition/context",
                                        "valueReference": {
                                            "reference": "EpisodeOfCare/"
                                        }
                                    }
                                ],
                                "valueQuantity": {
                                    "value": 4.388936048793813,
                                    "unit": "%",
                                    "system": "http://unitsofmeasure.org",
                                    "code": "%"
                                },
                                "referenceRange": [
                                    {
                                        "high": {
                                            "value": 0.3503903735342706
                                        }
                                    }
                                ]
                            }
                        }
                    ]
                }
            ]
        }
    ]
}
```

## Docker

You can create and run a docker image as following:

```
mvn clean install
docker build -t srdc/smart-cds -f ./docker/Dockerfile .
docker compose -f ./docker/docker-compose.yml up -d
```

## Disclaimer

This application is a demonstration of CDS-Hooks integration with FHIR servers and incorporates QRISK, ADVANCE, ACC/AHA,
and SCORE2 algorithms for illustrative purposes only. It is **not intended** for use in real-world clinical decision-making.
The results generated by this application should not be considered as medical advice.

## References

- OnFHIR: https://onfhir.io
- HL7 FHIR: https://hl7.org/fhir/
- CDS-Hooks: https://cds-hooks.org
- QRISK: https://www.qrisk.org/index.php
- ACC/AHA: https://www.acc.org/Guidelines
- SCORE2: https://www.escardio.org/Education/Practice-Tools/CVD-prevention-toolbox/SCORE-Risk-Charts
