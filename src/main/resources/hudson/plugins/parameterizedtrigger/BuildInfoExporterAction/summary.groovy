package hudson.plugins.parameterizedtrigger.BuildInfoExporterAction

import hudson.model.BallColor
import hudson.model.Result;

def f=namespace(lib.FormTagLib)
def j=namespace(lib.JenkinsTagLib)
def l=namespace(lib.LayoutTagLib)

def builds = my.triggeredBuilds
if(builds.size() > 0) {
	h2("Subproject Builds");

	ul(style:"list-style-type: none;") {
		for (item in builds.sort{it != null && it.hasProperty('description') ? it.description : ''}) {
//		for (item in builds.sort{it.timeInMillis}) {
			li {
				if (item != null) {

					a(href:"${rootURL}/${item.project.url}", class:"model-link") {
						text(item.project.displayName)
					}
					a(href:"${rootURL}/${item.url}", class:"model-link") {
						img(src:"${imagesURL}/16x16/${item.buildStatusUrl}",
								alt:"${item.iconColor.description}", height:"16", width:"16")
						text(item.displayName)
					}

					if (item.hasProperty('description') && item.description != null && item.description != '') {
						raw(' ' + item.description)
	//						item.properties.each { prop, val ->
	//							text(prop + '=' + val + " <br/> ")
	//						}
					}
				}
			}
		}
	}
}

def elasticSearchUri = my.elasticSearchUri
def blockingBuildRefs = my.blockingBuildRefs
if(elasticSearchUri != null && blockingBuildRefs.size() > 0) {
	h2("Subproject Builds (Elasticsearch)");

	ul(style: "list-style-type: none;") {
		for (item in blockingBuildRefs.sort {it.buildDescription}) {
			if (item.buildResult == Result.SUCCESS) {
				iconFileName = BallColor.BLUE.image
				iconDescription = BallColor.BLUE.description
			}
			else if (item.buildResult == Result.FAILURE) {
				iconFileName = BallColor.RED.image
				iconDescription = BallColor.RED.description
			}
			else if (item.buildResult == Result.ABORTED) {
				iconFileName = BallColor.ABORTED.image
				iconDescription = BallColor.ABORTED.description
			}
			else {
				iconFileName = BallColor.DISABLED.image
				iconDescription = BallColor.DISABLED.description
			}
			li {
				if (item != null) {
					a(href: "${rootURL}/${item.projectName}", class: "model-link") {
						text(item.projectName)
						a(href: "${elasticSearchUri}/${item.projectName}_${item.buildNumber}?_source_includes=message", class: "model-link") {
							img(src:"${imagesURL}/16x16/${iconFileName}",
								alt:"${iconDescription}", height:"16", width:"16")
							text('#' + item.buildNumber)
						}

						if (item.buildDescription) {
							raw(' ' + item.buildDescription)
						}
					}
				}
			}
		}
	}
}

def projects = my.triggeredProjects
if (projects.size() > 0) {
	h2("Subprojects triggered but not blocked for");

	ul(style:"list-style-type: none;") {
		for (item in projects) {
			li {
				print "${item}"
				if (item != null) {
					a(href:"${rootURL}/${item.url}", class:"model-link") {
						text(item.displayName)
					}
				}
			}
		}
	}
}

