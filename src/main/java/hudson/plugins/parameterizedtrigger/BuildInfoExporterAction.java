/*
 * The MIT License
 *
 * Copyright (c) 2011-2, Jørgen P. Tjernø <jorgenpt@gmail.com>
 *                       Chris Johnson
 *                       Geoff Cummings
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.parameterizedtrigger;

import hudson.EnvVars;
import hudson.model.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import hudson.util.DescribableList;
import jenkins.model.Jenkins;
import jenkins.plugins.logstash.LogstashConfiguration;
import jenkins.plugins.logstash.LogstashNotifier;
import jenkins.plugins.logstash.persistence.ElasticSearchDao;
import jenkins.plugins.logstash.persistence.LogstashIndexerDao;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.net.URI;
import java.net.URISyntaxException;
import org.apache.http.client.utils.URIBuilder;

@ExportedBean
public class BuildInfoExporterAction implements EnvironmentContributingAction {

  public static final String JOB_NAME_VARIABLE = "LAST_TRIGGERED_JOB_NAME";
  public static final String ALL_JOBS_NAME_VARIABLE = "TRIGGERED_JOB_NAMES";
  public static final String BUILD_NUMBER_VARIABLE_PREFIX = "TRIGGERED_BUILD_NUMBER_";
  public static final String ALL_BUILD_NUMBER_VARIABLE_PREFIX = "TRIGGERED_BUILD_NUMBERS_";
  public static final String BUILD_RESULT_VARIABLE_PREFIX = "TRIGGERED_BUILD_RESULT_";
  public static final String BUILD_RUN_COUNT_PREFIX = "TRIGGERED_BUILD_RUN_COUNT_";
  public static final String RUN = "_RUN_";
  //now unused as part of map
  private transient String buildName;
  private transient int buildNumber;

  // used in version =< 2.21.
  // this is now migrated to this.builds.
  private transient Map<String, List<BuildReference>> buildRefs;

  private List<BuildReference> builds;
  private BuildReference lastReference;

  public BuildInfoExporterAction(BuildReference buildRef) {
    super();

    this.builds = new ArrayList<BuildReference>();
    addBuild(buildRef);
    lastReference = buildRef;
  }

  public BuildInfoExporterAction(String buildName, int buildNumber, AbstractBuild<?, ?> parentBuild, Result buildResult, String buildDescription) {
    this(new BuildReference(buildName, buildNumber, buildResult, buildDescription));
  }

  static BuildInfoExporterAction addBuildInfoExporterAction(AbstractBuild<?, ?> parentBuild, String triggeredProject, int buildNumber, Result buildResult, String buildDescription) {
    BuildInfoExporterAction action = parentBuild.getAction(BuildInfoExporterAction.class);
    if (action == null) {
      action = new BuildInfoExporterAction(triggeredProject, buildNumber, parentBuild, buildResult, buildDescription);
      parentBuild.getActions().add(action);
    } else {
      action.addBuildReference(triggeredProject, buildNumber, buildResult, buildDescription);
    }
    return action;
  }

  static BuildInfoExporterAction addBuildInfoExporterAction(AbstractBuild<?, ?> parentBuild, String triggeredProject) {
    BuildInfoExporterAction action = parentBuild.getAction(BuildInfoExporterAction.class);
    if (action == null) {

      action = new BuildInfoExporterAction(new BuildReference(triggeredProject));
      parentBuild.getActions().add(action);
    } else {
      action.addBuildReference(new BuildReference(triggeredProject));
    }
    return action;
  }

  private void addBuild(BuildReference br) {
    this.builds.add(br);

    if (br.buildNumber != 0) {
      this.lastReference = br;
    }
  }

  public void addBuildReference(String triggeredProject, int buildNumber, Result buildResult, String buildDescription) {
    BuildReference buildRef = new BuildReference(triggeredProject, buildNumber, buildResult, buildDescription);
    addBuild(buildRef);
  }

  public void addBuildReference(BuildReference buildRef) {
    addBuild(buildRef);
  }

  public static class BuildReference {

    public final String projectName;
    public final int buildNumber;
    public final Result buildResult;
    public final String buildDescription;

    public BuildReference(String projectName, int buildNumber, Result buildResult, String buildDescription) {
      this.projectName = projectName;
      this.buildNumber = buildNumber;
      this.buildResult = buildResult;
      this.buildDescription = buildDescription;
    }

    public BuildReference(final String projectName) {
      this.projectName = projectName;
      this.buildNumber = 0;
      this.buildResult = Result.NOT_BUILT;
      this.buildDescription = "";
    }
  }

  @Override
  public String getIconFileName() {
    return null;
  }

  @Override
  public String getDisplayName() {
    return null;
  }

  @Override
  public String getUrlName() {
    return null;
  }

  @Override
  public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {

    // Note: this will only indicate the last project in the list that is ran
    env.put(JOB_NAME_VARIABLE, lastReference.projectName.replaceAll("[^a-zA-Z0-9]+", "_"));
    //all projects triggered.
    // this should not include projects that donot have a build item.
    String sanatizedProjectList = getProjectListString(",");
    env.put(ALL_JOBS_NAME_VARIABLE, sanatizedProjectList);

    for (String project : getProjectsWithBuilds()) {
      // for each project add the following variables once
      // all buildnumbers, lastbuildnumber
      // all Run results, last build result
      String sanatizedBuildName = project.replaceAll("[^a-zA-Z0-9]+", "_");
      List<BuildReference> refs = getBuildRefs(project);

      env.put(ALL_BUILD_NUMBER_VARIABLE_PREFIX + sanatizedBuildName, getBuildNumbersString(refs, ","));
      env.put(BUILD_RUN_COUNT_PREFIX + sanatizedBuildName, Integer.toString(refs.size()));
      for (BuildReference br : refs) {
        if (br.buildNumber != 0) {
          String tiggeredBuildRunResultKey = BUILD_RESULT_VARIABLE_PREFIX + sanatizedBuildName + RUN + Integer.toString(br.buildNumber);
          env.put(tiggeredBuildRunResultKey, br.buildResult.toString());
        }
      }
      BuildReference lastBuild = null;
      for (int i = (refs.size()); i > 0; i--) {
        if (refs.get(i - 1).buildNumber != 0) {
          lastBuild = refs.get(i - 1);
        }
        break;
      }
      if (lastBuild != null) {
        env.put(BUILD_NUMBER_VARIABLE_PREFIX + sanatizedBuildName, Integer.toString(lastBuild.buildNumber));
        env.put(BUILD_RESULT_VARIABLE_PREFIX + sanatizedBuildName, lastBuild.buildResult.toString());
      }
    }
  }

  private List<BuildReference> getBuildRefs(String project) {
    List<BuildReference> refs = new ArrayList<BuildReference>();
    for (BuildReference br : builds) {
      if (br.projectName.equals(project)) refs.add(br);
    }
    return refs;
  }

  /**
   * Gets all the builds triggered from this one, filters out the items that
   * were non blocking, which we don't have a builds for. Used in the UI for see
   * Summary.groovy
   *
   * @return a list of builds that are triggered by this build. May contains null if a project or a build is deleted.
   */
  @Exported(visibility = 1)
  public List<Run<?, ?>> getTriggeredBuilds() {

    List<Run<?, ?>> builds = new ArrayList<Run<?, ?>>();

    for (BuildReference br : this.builds) {
      Job<?, ? extends Run<?, ?>> project =
              Jenkins.getInstance().getItemByFullName(br.projectName, Job.class);
      if (br.buildNumber != 0) {
        if (project != null) {
          Run<?, ?> run = project.getBuildByNumber(br.buildNumber);
          if (run != null) {
            builds.add(run);
          }
        }
      }
    }
    return builds;
  }

  /**
   * Gets all the projects triggered from this one, filters out the items that
   * were non blocking, which we don't have a builds for. Used in the UI for see
   * Summary.groovy
   *
   * @return a list of buildRefs that are triggered by this build. May contains null if a project or a build is deleted.
   */
  @Exported(visibility = 1)
  public List<BuildReference> getBlockingBuildRefs() {

    List<BuildReference> triggeredBuildRefs = new ArrayList<>();

    for (BuildReference br : this.builds) {
      if (br.buildNumber != 0) {
        triggeredBuildRefs.add(br);
      }
    }
    return triggeredBuildRefs;
  }

  /**
   * Gets all the projects that triggered from this one which were non blocking,
   * which we don't have a builds for. Does not include builds that are returned
   * in #link{getTriggeredBuilds} Used in the UI for see Summary.groovy
   *
   * @return List of Projects that are triggered by this build. May contains null if a project is deleted.
   */
  @Exported(visibility = 1)
  public List<Job<?, ?>> getTriggeredProjects() {
    List<Job<?, ?>> projects = new ArrayList<Job<?, ?>>();

    for (BuildReference br : this.builds) {
      if (br.buildNumber == 0) {
        Job<?, ? extends Run<?, ?>> project =
                Jenkins.getInstance().getItemByFullName(br.projectName, Job.class);
        projects.add(project);
      }
    }
    return projects;
  }

  /**
   * Handle cases from older builds so that they still add old variables if
   * needed to. Should not show any UI as there will be no data added.
   *
   * @return
   */
  public Object readResolve() {
    if (this.lastReference == null) {
      this.lastReference = new BuildReference(this.buildName, this.buildNumber, Result.NOT_BUILT, "");
    }
    if (this.builds == null) {
      this.builds = new ArrayList<BuildReference>();
    }
    if (this.buildRefs != null) {
      for (List<BuildReference> buildReferences : buildRefs.values()) {
        this.builds.addAll(buildReferences);
      }
    }
    return this;
  }

  /**
   * Gets a string for all of the build numbers
   *
   * @param refs      List of build references to process.
   * @param separator
   * @return String containing all the build numbers from refs, never null but
   * can be empty
   */
  private String getBuildNumbersString(List<BuildReference> refs, String separator) {
    StringBuilder buf = new StringBuilder();
    boolean first = true;

    for (BuildReference s : refs) {
      if (s.buildNumber != 0) {
        if (first) {
          first = false;
        } else {
          buf.append(separator);
        }
        buf.append(s.buildNumber);
      }
    }
    return buf.toString();
  }

  /**
   * Get a list of projects as a string using the separator
   *
   * @param separator
   * @return list of projects separated by separator
   */
  protected String getProjectListString(String separator) {
    Set<String> refs = getProjectsWithBuilds();
    StringBuilder buf = new StringBuilder();
    boolean first = true;

    for (String s : refs) {
      if (first) {
        first = false;
      } else {
        buf.append(separator);
      }
      buf.append(s.replaceAll("[^a-zA-Z0-9]+", "_"));
    }
    return buf.toString();
  }

  /**
   * Gets the unique set of project names that have a linked build.
   *
   * @return Set of project names that have at least one build linked.
   */
  @Exported(visibility = 1)
  public Set<String> getProjectsWithBuilds() {
    Set<String> projects = new HashSet<String>();

    for (BuildReference br : this.builds) {
      if (br.buildNumber != 0) {
        projects.add(br.projectName);
      }
    }
    return projects;
  }

  /**
   * Gets the ElasticSearch URL from logstash plugin configuration
   * Used in the UI for see Summary.groovy
   *
   * @return ElasticSearch URL if defined in the Logstash plugin
   */
  @Exported(visibility = 1)
  public String getElasticSearchUri() {
    LogstashConfiguration logstashConfig = (LogstashConfiguration) Jenkins.getActiveInstance().getDescriptor("jenkins.plugins.logstash.LogstashConfiguration");

    LogstashIndexerDao logstashIndexer = logstashConfig.getIndexerInstance();
    if (logstashIndexer instanceof ElasticSearchDao) {
      URI logstashUri = ((ElasticSearchDao) logstashIndexer).getUri();
      if (logstashUri != null) {
        String[] path = logstashUri.getPath().split("/");
        String wildcardPath = path[1] + "*/" + path[2] + "/_search";
        URIBuilder queryUri = new URIBuilder(logstashUri);
        queryUri.setPath(wildcardPath);
        try {
          logstashUri = queryUri.build();
        } catch (URISyntaxException e) {
          // pass
        }

        return logstashUri.toString();
      }
    }
    return null;
  }

  /**
   * Check if logstash plugin is enabled for this project, and globally
   * Used in the UI, see summary.groovy
   *
   * @return boolean
   */
  @Exported(visibility = 1)
  public boolean isLogstashEnabled(String projectName) {
    LogstashConfiguration logstashConfig = (LogstashConfiguration) Jenkins.getActiveInstance().getDescriptor("jenkins.plugins.logstash.LogstashConfiguration");
    if (logstashConfig.isEnableGlobally()) {
      return true;
    }

    Job<?, ? extends Run<?, ?>> project =
            Jenkins.getInstance().getItemByFullName(projectName, Job.class);

    if (project instanceof AbstractProject) {
      DescribableList publishers = ((AbstractProject) project).getPublishersList();
      if (publishers.get(LogstashNotifier.class) != null) {
        return true;
      }
      // I know... really ugly
      if (project.getName().startsWith("sb.run")) {
        return true;
      }
    }
    return false;
  }
}

