import PropTypes from 'prop-types';
import React from 'react';
import { connect } from 'react-redux';
import { changeEditFormField, loadEditPage } from '../../../actions';
import ActionGroup from '../../../components/base/page/action/Group';
import TabNavigation from '../../../components/base/page/edit/TabNavigation';
import LayoutGroup from '../../../components/base/page/layout/Group';
import PageNavigation from '../../../components/base/page/Navigation';
import { Alert, Container, TabContent, TabPane, } from '../../../components/design';
import LoadingContainer from '../../../components/design/loading-container';
import style from '../../ProjectForge.module.scss';

class EditPage extends React.Component {
    constructor(props) {
        super(props);

        this.state = {
            activeTab: 'edit',
        };

        this.toggleTab = this.toggleTab.bind(this);
    }

    componentDidMount() {
        const { load, match } = this.props;

        load(match.params.category, match.params.id);
    }

    toggleTab(event) {
        const { activeTab } = this.state;

        if (activeTab === event.target.id) {
            return;
        }

        this.setState({
            activeTab: event.target.id,
        });
    }

    render() {
        const { activeTab } = this.state;
        const {
            changeDataField,
            data,
            error,
            loading,
            ui,
        } = this.props;

        if (error) {
            return (
                <Alert color="danger">
                    <h4>[Failed to Fetch Design]</h4>
                    <p>[Description Here]</p>
                </Alert>
            );
        }

        return (
            <LoadingContainer loading={loading}>
                <PageNavigation current={ui.title} />
                <TabNavigation
                    tabs={{
                        edit: ui.title,
                        history: '[History]',
                    }}
                    toggleTab={this.toggleTab}
                    activeTab={activeTab}
                />
                <TabContent
                    activeTab={activeTab}
                    className={style.tabContent}
                >
                    <TabPane tabId="edit">
                        <Container fluid>
                            <LayoutGroup
                                content={ui.layout}
                                data={data}
                                changeDataField={changeDataField}
                            />
                            <ActionGroup actions={ui.actions} />
                        </Container>
                    </TabPane>
                </TabContent>
            </LoadingContainer>
        );
    }
}

EditPage.propTypes = {
    changeDataField: PropTypes.func.isRequired,
    match: PropTypes.shape({}).isRequired,
    load: PropTypes.func.isRequired,
    ui: PropTypes.shape({}).isRequired,
    error: PropTypes.string,
    data: PropTypes.shape({}),
    loading: PropTypes.bool,
};

EditPage.defaultProps = {
    data: [],
    error: undefined,
    loading: false,
};

const mapStateToProps = state => ({
    ui: state.editPage.ui,
    error: state.editPage.error,
    loading: state.editPage.loading,
    data: state.editPage.data,
});

const actions = {
    load: loadEditPage,
    changeDataField: changeEditFormField,
};

export default connect(mapStateToProps, actions)(EditPage);