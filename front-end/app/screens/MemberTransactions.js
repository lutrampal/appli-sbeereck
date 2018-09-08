import React from 'react';
import {Alert, AsyncStorage, FlatList} from 'react-native';
import EStyleSheet from 'react-native-extended-stylesheet';
import {Container} from '../components/Container';
import {Header} from '../components/Header';
import {YellowButton} from '../components/YellowButton';
import {SearchBox} from '../components/SearchBox';
import {Loading} from '../components/Loading';
import * as RNFS from "react-native-fs"
import {open} from "react-native-share"
import TransactionItem from "../components/TransactionItem/TransactionItem"
import Toast from "react-native-simple-toast"

EStyleSheet.build({
    $mainBackground: '#F9F9F9'
})

export default class Home extends React.Component {
    constructor(props){
        super(props);
        this.state = {
            appHost: "",
            appToken: "",

            isSearching: false,
            searchValue: "",

            initialTransactions: [],
            transactions: [],

            loading: false,
        };
    }

    static navigationOptions = {
        disableGestures: true,
        drawerLockMode: 'locked-closed',
    }

    async componentDidMount() {
        this.setState({ loading: true });

        try {
            let host = await AsyncStorage.getItem('@SbeerEck:host');
            const token = await AsyncStorage.getItem('@SbeerEck:token');

            if (host != null) {
                host = host.replace(" ", "");
                this.setState({ appHost: host });
            }
            if (token != null)
                this.setState({ appToken: token });

            await this.checkConnection();

            if (this.state.connected)
            {
                this.initiatesTransactions();
            }
        } catch (error) {
            Toast.show("Erreur lors du chargement des paramètres.\n" + error);
            this.setState({ loading: false });
        }
    }

    render() {
        return(
            <Container>
                <Header title="Transactions" leftButtonIcon="arrow-left" leftButtonAction={() => { this.props.navigation.goBack() }} rightButtonAction={() => { this.setState({ isSearching: !this.state.isSearching, searchValue: "", transactions: this.state.initialTransactions }) }} rightButtonIcon="magnify" />

                <SearchBox showBox={this.state.isSearching}
                searchText={this.state.searchValue}
                editText={(text) => {
                    this.setState(
                        {
                            searchValue: text,
                            transactions: this.state.initialTransactions.filter((line) => ((this.preg(line.party_name) + this.preg(line.label) + this.preg(line.amount.toString())).includes(this.preg(text))))
                        }
                    );
                    }}/>

                <FlatList
                    refreshing={false}
                    onRefresh={this.initiatesTransactions.bind(this)}
                    data={this.state.transactions}
                    extraData={this.state}
                    keyExtractor={(item, index) => index.toString()}
                    renderItem={({ item }) => <TransactionItem bottom_text={item.party_name} item={item} onClickDelete={(item) => {
                        Alert.alert(
                            'Supprimer la transaction',
                            'Êtes-vous sûr de vouloir supprimer la transaction de '
                            + this.props.navigation.state.params.memberFirstName + ' '
                            + this.props.navigation.state.params.memberLastName + ' : "' + item.label + '" ?',
                            [
                                { text: 'Annuler', onPress: () => { }, style: 'cancel' },
                                {
                                    text: 'Supprimer', onPress: () => {
                                        this.setState({ loading: true }); // TODO : add log with the staff id
                                        this.deleteTransaction(item.id);
                                    }
                                },
                            ],
                            { cancelable: false }
                        )
                    }}/>}
                />

                <YellowButton buttonIcon="table" buttonAction={() => this.exportMember()}/>

                <Loading shown={this.state.loading} />
            </Container>
        );
    }

    async initiatesTransactions() {
        await this.checkConnection();

        this.setState({loading: true})

        try {
            let response = await fetch('https://' + this.state.appHost + '/members/' + this.props.navigation.state.params.memberId + '/transactions' ,
                {
                    headers: {
                        'authentication-token': this.state.appToken
                    }
                });

            let request = await response.json();

            this.setState({ initialTransactions: request });

            this.setState({ transactions: this.state.initialTransactions, loading: false, searchValue: "" });
        } catch (error) {
            this.setState({ loading: false, searchValue: "" });
            console.log(error);
        }
    }

    async checkConnection() {
        try {
            let response = await fetch('https://' + this.state.appHost + '/default_price/normal_beer',
                {
                    headers: {
                        'authentication-token': this.state.appToken
                    }
                });

            this.setState({ connected: true });

        } catch (error) {
            this.setState({ connected: false, loading: false });
            this.props.navigation.navigate("Parameters");
        }
    }

    async deleteTransaction(transactionId) {
        await this.checkConnection();

        try {
            if (this.state.connected) {
                let response = await fetch('https://' + this.state.appHost + '/transactions/' + transactionId,
                    {
                        method: "delete",
                        headers: {
                            'content-type': "application/json",
                            'authentication-token': this.state.appToken
                        }
                    });

                this.initiatesTransactions();
                this.props.navigation.state.params.refreshItems();
                Toast.show("Transaction supprimée !");
            }
        } catch (error) {
            console.log(error);
            this.setState({ loading: false });
        }
    }

    preg(chaine) {
        let tab = {
            "é": "e", "è": "e", "ê": "e", "ë": "e",
            "ç": "c",
            "à": "a", "â": "a", "ä": "a",
            "î": "i", "ï": "i",
            "ù": "u",
            "ô": "o", "ó": "o", "ö": "o",
            " ": "", "'": "", "-": ""
        };

        for (i in tab) {
            chaine = chaine.toLowerCase().replace(new RegExp(i, "gi"), tab[i])
        }
        return chaine;
    }

    exportMember() {
        let csv = "Soirée,Date et heure,Montant,Libellé\n"
        /* CSV formatting:
         *  - each value is double quoted to escape commas
         *  - each double quote in value is doubled to escape it
         */
        this.state.transactions.forEach((t) => {
            csv += '"' + t.party_name + '","' + t.timestamp + '","'
                + t.amount.toString().replace(/\./g, ',').replace(/ /g,'') + '","' + t.label + '"\n'
        })
        let path = RNFS.DocumentDirectoryPath + '/'
            + (this.props.navigation.state.params.memberFirstName + ' ' + this.props.navigation.state.params.memberLastName)
                .replace(/[^\w\s-]/g, '').trim().replace(/[^\w\s-]/g, '-')
            + '.csv';
        RNFS.writeFile(path, csv, 'ascii')
            .then((success) => {
                open({
                    title: 'Exporter les transactions '
                        + (/[aeiouyAEIOUY]/.test(this.props.navigation.state.params.memberFirstName[0]) ? "d'" : "de ")
                        + this.props.navigation.state.params.memberFirstName + ' '
                        + this.props.navigation.state.params.memberLastName,
                    url: 'file://' + path,
                    type: 'text/csv',
                    showAppsToView: true,
                })
            })
            .catch((err) => {
                console.error(err.message);
            });

    }
}