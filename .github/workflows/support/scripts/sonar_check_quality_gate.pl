#!/usr/bin/perl

use strict;
use warnings;
use v5.18;

use JSON::Parse "parse_json";
use Mozilla::CA;
use LWP::UserAgent;
use HTTP::Request::Common;
use URI;
use Env;

no warnings 'experimental::smartmatch';

# Define CircleCI Environment Variable Constants
use constant {
    CIRCLE_BRANCH     => "CIRCLE_BRANCH",
    CIRCLE_TAG        => "CIRCLE_TAG",
    SONAR_SERVER      => "SONAR_SERVER",
    SONAR_TOKEN       => "SONAR_TOKEN",
    SONAR_PROJECT_KEY => "SONAR_PROJECT_KEY",
    SONAR_TASK_ID     => "SONAR_TASK_ID"
};

# Define Default Values for Sonar Constants
use constant {
    DEFAULT_SONAR_SERVER      => "https://sonarcloud.io",
    SONAR_DASHBOARD_LINK_FILE => "/tmp/sonar_dashboard_link.txt"
};

# Define Sonar Template Constants
use constant {
    SONAR_QG_PRJ_STATUS_URI  => "api/qualitygates/project_status",
    SONAR_CE_TASK_STATUS_URI => "api/ce/task",
    SONAR_DASHBOARD_URI      => "dashboard",
    SONAR_ARG_DASHBOARD_ID   => "id",
    SONAR_ARG_BRANCH         => "branch",
    SONAR_ARG_PROJ_KEY       => "projectKey",
    SONAR_ARG_ANALYSIS_ID    => "analysisId",
    SONAR_ARG_TASK_ID        => "id"
};

# Define Task Status Constants
use constant {
    SONAR_TSK_SUCCESS  => "SUCCESS",
    SONAR_TSK_FAILED   => "FAILED",
    SONAR_TSK_CANCELED => "CANCELED"
};

# Define QG Status Constants
use constant {
    SONAR_QG_OK    => "OK",
    SONAR_QG_WARN  => "WARN",
    SONAR_QG_ERROR => "ERROR",
    SONAR_QG_NONE  => "NONE"
};

# Define Exit Code Constants
use constant {
    EX_OK                  => 0,
    EX_BRANCH_MISSING      => 8,
    EX_PROJECT_KEY_MISSING => 9,
    EX_TOKEN_MISSING       => 10,
    EX_HTTP_ERROR          => 11,
    EX_TASK_ID_MISSING     => 12,
    EX_TASK_LOOKUP_FAILED  => 13,
    EX_ANALYSIS_ID_MISSING => 14,
    EX_QUALITY_GATE_FAILED => 63
};


# Define Subroutines

sub sonar_create_url {
    my ($path, $query) = @_;
    my $server = $ENV{&SONAR_SERVER} || DEFAULT_SONAR_SERVER;

    my $uri = URI->new($server);
    $uri->path($path);
    $uri->query_form($query);

    return $uri;
}

sub sonar_create_qg_project_url {
    my $branch = $ENV{&CIRCLE_BRANCH};
    my $projectKey = $ENV{&SONAR_PROJECT_KEY};

    if (!defined($branch) || chomp($branch) eq '') {
        sonar_display_message(EX_BRANCH_MISSING);
        exit(EX_BRANCH_MISSING);
    }

    if (!defined($projectKey) || chomp($projectKey) eq '') {
        sonar_display_message(EX_PROJECT_KEY_MISSING);
        exit(EX_PROJECT_KEY_MISSING);
    }

    return sonar_create_url(SONAR_QG_PRJ_STATUS_URI, {
        &SONAR_ARG_BRANCH   => $branch,
        &SONAR_ARG_PROJ_KEY => $projectKey
    });
}

sub sonar_create_qg_analysis_url {
    my ($analysisId) = @_;

    if (!defined($analysisId) || chomp($analysisId) eq '') {
        sonar_display_message(EX_ANALYSIS_ID_MISSING);
        exit(EX_ANALYSIS_ID_MISSING);
    }

    return sonar_create_url(SONAR_QG_PRJ_STATUS_URI, {
        &SONAR_ARG_ANALYSIS_ID => $analysisId
    });
}

sub sonar_create_task_lookup_url {
    my $taskId = $ENV{&SONAR_TASK_ID};

    if (!defined($taskId) || chomp($taskId) eq '') {
        sonar_display_message(EX_TASK_ID_MISSING);
        exit(EX_TASK_ID_MISSING);
    }

    return sonar_create_url(SONAR_CE_TASK_STATUS_URI, {
        &SONAR_ARG_TASK_ID => $taskId
    });
}

sub sonar_create_dashboard_url {
    my $projectKey = $ENV{&SONAR_PROJECT_KEY};
    my $branch = $ENV{&CIRCLE_BRANCH};

    my %params = (
        &SONAR_ARG_BRANCH       => $branch,
        &SONAR_ARG_DASHBOARD_ID => $projectKey
    );

    if (!defined($projectKey) || chomp($projectKey) eq '') {
        delete($params{&SONAR_ARG_DASHBOARD_ID});
    }

    if (!defined($branch) || chomp($branch) eq '') {
        delete($params{&SONAR_ARG_BRANCH});
    }

    my $uri = sonar_create_url(SONAR_DASHBOARD_URI, { %params });

    open(my $fh, '>', &SONAR_DASHBOARD_LINK_FILE);
    print $fh $uri->as_string;
    close($fh);

    return $uri;
}

sub sonar_authenticated_request {
    my ($uri) = @_;
    my $token = $ENV{&SONAR_TOKEN};

    if (!defined($token) || chomp($token) eq '') {
        exit(EX_TOKEN_MISSING);
    }

    my $req = GET $uri;
    $req->authorization_basic($token, "");

    return $req;
}

sub sonar_web_poll {
    my ($ua, $req, $limit, $testFn) = @_;
    my $limitCtr = 0;

    if (!defined($testFn)) {
        $testFn = sub {
            my ($req, $resp) = @_;
            return $resp->code == 404;
        };
    }

    if (!defined($limit)) {
        $limit = 5;
    }

    my $resp = $ua->request($req);

    while ($testFn->($req, $resp) && ++$limitCtr <= $limit) {
        sleep 1;
        $resp = $ua->request($req);
    }

    if (HTTP::Status::is_error($resp->code)) {
        print "\n\nRequest:\n";
        print '-' x 120;
        print "\n";
        print $req->as_string();

        print "\n\nResponse:\n";
        print '-' x 120;
        print "\n";
        print $resp->as_string();

        sonar_display_message(EX_HTTP_ERROR);
        exit(EX_HTTP_ERROR);
    }

    return $resp;
}

sub sonar_resolve_analysis_id {
    my ($ua) = @_;

    my $uri = sonar_create_task_lookup_url;
    my $req = sonar_authenticated_request($uri);
    my $resp = sonar_web_poll($ua, $req, 20, sub {
        my ($req, $resp) = @_;

        if ($resp->code == 404) {
            return 1;
        }
        elsif (HTTP::Status::is_error($resp->code)) {
            return 0;
        }

        my $payload = parse_json($resp->content);
        return !($payload->{task}->{status} ~~ [ &SONAR_TSK_SUCCESS, &SONAR_TSK_CANCELED, &SONAR_TSK_FAILED ]);
    });

    my $result = parse_json($resp->content);

    if ($result->{task}->{status} eq SONAR_TSK_SUCCESS) {
        return $result->{task}->{analysisId};
    }

    sonar_display_message(EX_TASK_LOOKUP_FAILED);
    exit(EX_TASK_LOOKUP_FAILED);
}

sub sonar_check_quality_gate {
    my ($ua, $analysisId) = @_;

    my $uri = sonar_create_qg_analysis_url($analysisId);
    my $req = sonar_authenticated_request($uri);
    my $resp = sonar_web_poll($ua, $req, 5);

    my $result = parse_json($resp->content);

    return $result->{projectStatus}->{status} ~~ [ &SONAR_QG_OK, &SONAR_QG_NONE ];
}

sub sonar_display_message {
    my ($errorCode) = @_;
    my $uri = sonar_create_dashboard_url;

    print "\n\n";
    print '-' x 150;
    print "\n";
    if ($errorCode == EX_OK) {
        print "\tSONAR QUALITY GATE:\tPASSED\n\tDashboard URL:\t\t" . $uri->as_string . "\n";
    }
    elsif ($errorCode == EX_QUALITY_GATE_FAILED) {
        print "\tSONAR QUALITY GATE:\tFAILED\n\tDashboard URL:\t\t" . $uri->as_string . "\n";
    }
    else {
        print "\tSONAR QUALITY GATE:\tUNHANDLED LOOKUP ERROR\n\tDIAGNOSTIC ERROR CODE:\t$errorCode\n";
    }
    print '-' x 150;
    print "\n";
}

sub main {
    # Create Global HTTPS Enabled UA
    my $ua = LWP::UserAgent->new(
        protocols_allowed   => [ 'https' ],
        timeout             => 10,
        ssl_verify_hostname => 1
    );

    # Provide basic initialization of the UserAgent
    $ua->env_proxy;
    $ua->agent("Swirlds/1.0");

    my $analysisId = sonar_resolve_analysis_id($ua);

    if (!sonar_check_quality_gate($ua, $analysisId)) {
        sonar_display_message(EX_QUALITY_GATE_FAILED);
        exit(EX_QUALITY_GATE_FAILED);
    }

    sonar_display_message(EX_OK);
    exit(EX_OK);
}

# Execute the main subroutine
main;