/*
 * (c) Copyright 2018 Micro Focus or one of its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.microfocus.adm.almoctane.ciplugins.gocd.report.nunit.v25.dom;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

/**
 * A culture info.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class NUnitEnvironment {

	@XmlAttribute(name = "nunit-version")
	private String nunitVersion;
	@XmlAttribute(name = "clr-version")
	private String clrVersion;
	@XmlAttribute(name = "os-version")
	private String osVersion;
	@XmlAttribute
	private String platform;
	@XmlAttribute
	private String cwd;
	@XmlAttribute(name = "machine-name")
	private String machineName;
	@XmlAttribute
	private String user;
	@XmlAttribute(name = "user-domain")
	private String userDomain;
}
