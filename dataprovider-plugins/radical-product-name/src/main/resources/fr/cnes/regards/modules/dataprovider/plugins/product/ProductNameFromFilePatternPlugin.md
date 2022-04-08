# Product name generator plugin

## Definition 

This plugin generates product name of each scanned file by an acquisition chain by analysing file name as a regexp pattern.
This plugin remove some configured groups from scanned file name based on the given file name pattern.

`Note : If file name pattern doesn't match scanned file name product name cannot be generated and acquisition of file fails`

Two parameters can be configured for this plugin :

 - fileNamePattern : Pattern used to match with scanned file name
 - groups : List of regexp groups to remove from scanned file name to generate product name

## Exemple
 
Configuration :
  - fileNamePattern : (.*_)(.*)_T2[a-z]{1}(.tar)
  - groups : 1,3

File scanned : G1_G2_T2b.tar

Result product name : G2_T2b

